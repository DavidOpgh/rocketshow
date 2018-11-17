package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.util.FileDurationGetter;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBSource;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handle storage, sorting, etc. of compositions and sets.
 */
@Service
public class DefaultCompositionService implements CompositionService {

    private final static Logger logger = Logger.getLogger(DefaultCompositionService.class);

    private final static String COMPOSITIONS_PATH = "compositions/";
    private final static String SETS_PATH = "sets/";

    private SettingsService settingsService;
    private PlayerService playerService;

    // All available compositions
    private List<Composition> compositionCache = new ArrayList<>();
    private List<Set> compositionSetCache = new ArrayList<>();

    public DefaultCompositionService(SettingsService settingsService, PlayerService playerService) {
        this.settingsService = settingsService;
        this.playerService = playerService;

        // Initialize the cache
        this.loadAllCompositions();
        this.loadAllSets();
    }

    private void sortCompositionCache() {
        compositionCache.sort(Comparator.comparing(Composition::getName));
    }

    private void sortSetCache() {
        compositionSetCache.sort(Comparator.comparing(Set::getName));
    }

    private void finalizeLoadedComposition(Composition composition, String name) {
        composition.setName(name);
    }

    @Override
    public Composition cloneComposition(Composition composition) throws Exception {
        // Return a deep cloned instance of the composition
        Composition clonedComposition;
        JAXBContext jaxbContext = JAXBContext.newInstance(Composition.class);

        JAXBSource source = new JAXBSource(jaxbContext, composition);

        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        clonedComposition = (Composition) unmarshaller.unmarshal(source);

        finalizeLoadedComposition(clonedComposition, composition.getName());

        return clonedComposition;
    }

    private Composition loadComposition(String name) throws Exception {
        Composition composition;

        logger.debug("Loading composition " + name + "...");

        // Load a composition
        JAXBContext jaxbContext = JAXBContext.newInstance(Composition.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        composition = (Composition) jaxbUnmarshaller.unmarshal(new File(settingsService.getSettings().getBasePath() + COMPOSITIONS_PATH + name));

        finalizeLoadedComposition(composition, name);

        logger.debug("Composition '" + name + "' successfully loaded");

        return composition;
    }

    private Set loadSet(String name) throws Exception {
        Set set;

        logger.debug("Loading set '" + name + "'...");

        // Load a set
        JAXBContext jaxbContext = JAXBContext.newInstance(Set.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        set = (Set) jaxbUnmarshaller.unmarshal(new File(settingsService.getSettings().getBasePath() + SETS_PATH + name));

        logger.info("Set '" + name + "' successfully loaded");

        return set;
    }

    @Override
    public Composition getComposition(String name) {
        for (Composition cachedComposition : compositionCache) {
            if (cachedComposition.getName().equals(name)) {
                return cachedComposition;
            }
        }

        return null;
    }

    @Override
    public Set getSet(String name) {
        for (Set cachedCompositionSet : compositionSetCache) {
            if (cachedCompositionSet.getName().equals(name)) {
                return cachedCompositionSet;
            }
        }

        return null;
    }

    private void updateSets() throws Exception {
        // Update all sets (remove deleted files, update playing times),
        // when a composition has been changed/deleted

        List<Set> compositionSets = getAllSets();

        for (Set set : compositionSets) {
            // Load the full set
            Set fullCompositionSet = loadSet(set.getName());
            saveSet(fullCompositionSet);
        }
    }

    @Override
    public List<Composition> getAllCompositions() {
        return compositionCache;
    }

    @Override
    public List<Set> getAllSets() {
        return compositionSetCache;
    }

    @Override
    public synchronized void loadAllCompositions() {
        File folder = new File(settingsService.getSettings().getBasePath() + COMPOSITIONS_PATH);
        File[] fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    try {
                        Composition composition = loadComposition(file.getName());
                        compositionCache.add(composition);
                    } catch (Exception e) {
                        logger.error("Could not load composition '" + file.getName() + "'", e);
                    }
                }
            }
        }

        sortCompositionCache();
    }

    @Override
    public synchronized void loadAllSets() {
        File folder = new File(settingsService.getSettings().getBasePath() + SETS_PATH);
        File[] fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    Set set = new Set();
                    set.setName(file.getName());

                    compositionSetCache.add(set);
                }
            }
        }

        sortSetCache();
    }

    @Override
    public synchronized void saveComposition(Composition composition) throws Exception {
        // Get the duration of each file
        ExecutorService executor = Executors.newFixedThreadPool(30);

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            Runnable fileDurationGetter = new FileDurationGetter(compositionFile);
            executor.execute(fileDurationGetter);
        }

        executor.shutdown();

        // Wait until all threads are finished
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        // Set the duration of the composition to the maximum duration of the
        // files
        long maxDuration = 0;

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.getDurationMillis() > maxDuration) {
                maxDuration = compositionFile.getDurationMillis();
            }
        }
        composition.setDurationMillis(maxDuration);

        // Save the composition in XML
        File file = new File(settingsService.getSettings().getBasePath() + COMPOSITIONS_PATH + composition.getName());
        JAXBContext jaxbContext = JAXBContext.newInstance(Composition.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.marshal(composition, file);

        // Update the cache
        for (Composition cachedComposition : compositionCache) {
            if (cachedComposition.getName().equals(composition.getName())) {
                compositionCache.remove(cachedComposition);
                break;
            }
        }
        compositionCache.add(composition);
        sortCompositionCache();

        updateSets();

        logger.info("Composition '" + composition.getName() + "' saved");
    }

    @Override
    public synchronized void saveSet(Set set, boolean checkCompositions) throws Exception {
        if (checkCompositions) {
            // Update all composition information
            Iterator<SetComposition> iterator = set.getSetCompositionList().iterator();

            while (iterator.hasNext()) {
                SetComposition setComposition = iterator.next();

                Composition composition = null;

                try {
                    composition = loadComposition(setComposition.getName());
                } catch (Exception e) {
                    logger.error("Could not load composition", e);
                }

                if (composition == null) {
                    // The composition does not exist anymore (has been deleted)
                    // --> delete it from the set
                    iterator.remove();
                } else {
                    // The composition still exists -> update some information
                    setComposition.setDurationMillis(composition.getDurationMillis());
                }
            }
        }

        File file = new File(settingsService.getSettings().getBasePath() + SETS_PATH + set.getName());
        JAXBContext jaxbContext = JAXBContext.newInstance(Set.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.marshal(set, file);

        // Update the cache
        for (Set cachedCompositionSet : compositionSetCache) {
            if (cachedCompositionSet.getName().equals(set.getName())) {
                compositionSetCache.remove(cachedCompositionSet);
                break;
            }
        }
        compositionSetCache.add(set);
        sortSetCache();

        logger.info("Set '" + set.getName() + "' saved");
    }

    @Override
    public synchronized void saveSet(Set set) throws Exception {
        saveSet(set, true);
    }

    @Override
    public synchronized void deleteComposition(String name) throws Exception {
        // Delete the composition
        File file = new File(settingsService.getSettings().getBasePath() + COMPOSITIONS_PATH + name);

        if (file.exists()) {
            boolean result = file.delete();

            if (!result) {
                logger.error("Could not delete composition '" + name + "'");
            }
        }

        for (Composition cachedComposition : compositionCache) {
            if (cachedComposition.getName().equals(name)) {
                compositionCache.remove(cachedComposition);
                break;
            }
        }

        updateSets();

        // Set another composition, if we deleted the current one
        if (playerService.getCompositionName().equals(name)) {
            if (compositionCache.size() > 0) {
                playerService.setComposition(compositionCache.get(0));
            }
        }

        logger.info("Composition '" + name + "' deleted");
    }

    @Override
    public synchronized void deleteSet(String name) {
        // Delete the set
        File file = new File(settingsService.getSettings().getBasePath() + SETS_PATH + name);

        if (file.exists()) {
            boolean result = file.delete();

            if (!result) {
                logger.error("Could not delete set '" + name + "'");
            }
        }

        for (Set cachedCompositionSet : compositionSetCache) {
            if (cachedCompositionSet.getName().equals(name)) {
                compositionSetCache.remove(cachedCompositionSet);
                break;
            }
        }

        logger.info("Set '" + name + "' deleted");
    }

    public void nextComposition() {
        // Set the next composition (not set based)
        if (playerService.getCompositionName().length() > 0) {
            for (int i = 0; i < compositionCache.size(); i++) {
                if (compositionCache.get(i).getName().equals(playerService.getCompositionName())) {
                    if (compositionCache.size() > i + 1) {
                        try {
                            playerService.setComposition(compositionCache.get(i + 1));
                        } catch (Exception e) {
                            logger.error("Could not set the next composition", e);
                        }
                    }

                    return;
                }
            }
        }
    }

}
