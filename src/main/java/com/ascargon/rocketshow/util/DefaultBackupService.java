package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Manages backups.
 *
 * @author Moritz A. Vieli
 */
@Service
public class DefaultBackupService implements BackupService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultBackupService.class);

    private final List<String> backedUpFileList = Arrays.asList("compositions", "designer", "fixtures", "media", "session.xml", "settings.xml");

    private final SettingsService settingsService;
    private final DiskSpaceService diskSpaceService;
    private final ChunkedFileUploadService chunkedFileUploadService;
    private final RebootService rebootService;

    private final static String BACKUP_FILE_NAME = "backup.tar.gz";

    private final File backupFile;
    private final String workingDirectory;

    public DefaultBackupService(SettingsService settingsService, DiskSpaceService diskSpaceService, ChunkedFileUploadService chunkedFileUploadService, RebootService rebootService) {
        this.settingsService = settingsService;
        this.diskSpaceService = diskSpaceService;
        this.chunkedFileUploadService = chunkedFileUploadService;
        this.rebootService = rebootService;

        backupFile = new File(settingsService.getSettings().getBasePath() + BACKUP_FILE_NAME);
        workingDirectory = new File(settingsService.getSettings().getBasePath()).getName();
    }

    private void deleteBackupFile() throws Exception {
        // delete an eventually already created backup file
        if (backupFile.exists()) {
            boolean result = backupFile.delete();
            if (!result) {
                throw new Exception("Could not delete backup file '" + backupFile.getPath() + "'");
            }
        }
    }

    @Override
    public File create() throws Exception {
        File backupFile = new File(settingsService.getSettings().getBasePath() + BACKUP_FILE_NAME);

        deleteBackupFile();

        // check free disk space at least 50 % (because we use it for the backup file temporarily)
        DiskSpace diskSpace = diskSpaceService.get();

        if (diskSpace.getUsedMB() > 0) {
            // does not work on the mac e.g.
            long usedDiskSpacePercentage = Math.round(100 * diskSpace.getUsedMB() / (diskSpace.getUsedMB() + diskSpace.getAvailableMB()));
            if (usedDiskSpacePercentage > 50) {
                throw new Exception("Not enough free disk space available on the device to create the backup (at least 50% required).");
            }
        }

        // tar all backed up files
        // don't use zip, because the file permissions (e.g. execution rights) are not preserved easily with java
        ShellManager shellManager = new ShellManager(new String[]{"bash", "-c", "cd " + settingsService.getSettings().getBasePath() + " && tar -czpf " + BACKUP_FILE_NAME + " " + String.join(" ", backedUpFileList)});
        shellManager.getProcess().waitFor();
        shellManager.close();

        // Return the prepared zip
        return backupFile;
    }

    @Override
    public void restoreInit() throws Exception {
        deleteBackupFile();
    }

    @Override
    public void restoreAddChunk(InputStream inputStream) throws Exception {
        chunkedFileUploadService.handleChunk(inputStream, backupFile);
    }

    @Override
    public void restoreFinish() throws Exception {
        // automatic restore only works linux-based environments
        logger.info("Start restoring backup...");

        // run async
        Runnable task = () -> {
            ShellManager shellManager;

            try {
                // delete all backed up files/directories in the basepath
                shellManager = new ShellManager(new String[]{"bash", "-c", "rm -rf " + String.join(" ", backedUpFileList)});
                shellManager.getProcess().waitFor();
                shellManager.close();

                // unpack the backup-file
                shellManager = new ShellManager(new String[]{"tar", "-xzpf", settingsService.getSettings().getBasePath() + BACKUP_FILE_NAME});
                shellManager.getProcess().waitFor();
                shellManager.close();

                // delete the backup file
                shellManager = new ShellManager(new String[]{"rm", "-rf", settingsService.getSettings().getBasePath() + BACKUP_FILE_NAME});
                shellManager.getProcess().waitFor();
                shellManager.close();

                logger.info("Backup has been restored. Reboot...");

                rebootService.reboot();
            } catch (Exception e) {
                logger.error("Could not restore backup", e);
            }
        };

        Thread thread = new Thread(task);
        thread.start();
    }

}
