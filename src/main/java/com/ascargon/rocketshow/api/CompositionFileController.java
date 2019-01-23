package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.composition.CompositionFileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/file")
@CrossOrigin
public class CompositionFileController {

    private final CompositionFileService compositionFileService;

    public CompositionFileController(CompositionFileService compositionFileService) {
        this.compositionFileService = compositionFileService;
    }

    @GetMapping("list")
    public List<CompositionFile> getAll() {
        return compositionFileService.getAllFiles();
    }

    @PostMapping("upload")
    public CompositionFile upload(@RequestParam("file") MultipartFile file) throws IOException {
        return compositionFileService.saveFile(file.getInputStream(), file.getOriginalFilename());
    }

    @PostMapping("delete")
    public ResponseEntity<Void> saveSettings(@RequestParam("name") String name, @RequestParam("type") String type) {
        compositionFileService.deleteFile(name, type);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
