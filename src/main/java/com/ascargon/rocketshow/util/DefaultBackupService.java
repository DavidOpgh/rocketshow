package com.ascargon.rocketshow.util;

import com.ascargon.rocketshow.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
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

    private final SettingsService settingsService;
    private final DiskSpaceService diskSpaceService;
    private final ZipService zipService;
    private final ChunkedFileUploadService chunkedFileUploadService;

    private final static String BACKUP_FILE_NAME = "backup.zip";

    private final File backupFile;

    public DefaultBackupService(
            SettingsService settingsService,
            DiskSpaceService diskSpaceService,
            ZipService zipService,
            ChunkedFileUploadService chunkedFileUploadService
    ) {
        this.settingsService = settingsService;
        this.diskSpaceService = diskSpaceService;
        this.zipService = zipService;
        this.chunkedFileUploadService = chunkedFileUploadService;

        backupFile = new File(settingsService.getSettings().getBasePath() + BACKUP_FILE_NAME);
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
            long freeFiskSpacePercentage = Math.round(100 * diskSpace.getUsedMB() / (diskSpace.getUsedMB() + diskSpace.getAvailableMB()));
            if (freeFiskSpacePercentage < 50) {
                throw new Exception("Not enough free disk space available on the device to create the backup (at least 50% required).");
            }
        }
        logger.info(settingsService.getSettings().getBasePath());
        // zip the complete rocket show directory
        FileOutputStream fileOutputStream = new FileOutputStream(BACKUP_FILE_NAME);
        ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
        File fileToZip = new File(settingsService.getSettings().getBasePath());

        List<String> ignoreFileNameList = new ArrayList<>();
        ignoreFileNameList.add(BACKUP_FILE_NAME);
        ignoreFileNameList.add(LogDownloadService.LOGS_FILE_NAME);

        zipService.zipFile(fileToZip, fileToZip.getName(), zipOutputStream, ignoreFileNameList);
        zipOutputStream.close();
        fileOutputStream.close();

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
        // TODO
        logger.info("INIT RESTORE");
    }

}
