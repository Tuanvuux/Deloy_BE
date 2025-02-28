package com.example.spring_boot_react_demo.service.impl;

import com.example.spring_boot_react_demo.model.entity.Video;
import com.example.spring_boot_react_demo.repository.BackgroundRepo;
import com.example.spring_boot_react_demo.repository.VideoRepo;
import com.example.spring_boot_react_demo.service.CloudinaryService;
import com.example.spring_boot_react_demo.service.FFmpegService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level =  AccessLevel.PRIVATE, makeFinal = true)
public class FFmpegServiceImpl implements FFmpegService {
    CloudinaryService cloudinaryService;
    VideoRepo videoRepo;
    BackgroundRepo backgroundRepo;

    @Override
    public String extractAudio(String videoPath) {
        String audioPath = videoPath.replace(".mp4", ".mp3");
        String command = "ffmpeg -i " + videoPath + " -vn -acodec mp3 " + audioPath;

        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            return "Audio extraction completed, output saved to: " + audioPath;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Error during audio extraction";
        }
    }

    @Override
    public String cutAudio(MultipartFile file, String startTime, String endTime) {
        try{
            File tempFile = File.createTempFile("temp_audio_",".mp3");
            file.transferTo(tempFile);

            String outputFilePath = "output_" + tempFile.getName();
            String ffmpegCommand = String.format(
                    "ffmpeg -i %s -ss %s -to %s -c copy %s",
                    tempFile.getAbsolutePath(), startTime, endTime, outputFilePath
            );

            ProcessBuilder processBuilder = new ProcessBuilder(ffmpegCommand.split(" "));
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            process.waitFor();

            return "Audio file cut successfully. Output file: " + outputFilePath;
        }catch (IOException | InterruptedException e)  {
            e.printStackTrace();
            return "Error while cutting audio.";
        }
    }
    @Override
    public String mergeAudio(MultipartFile file1, MultipartFile file2) {
        try {
            File tempFile1 = File.createTempFile("temp_audio_1_", ".mp3");
            File tempFile2 = File.createTempFile("temp_audio_2_", ".mp3");
            file1.transferTo(tempFile1);
            file2.transferTo(tempFile2);

            File listFile = File.createTempFile("fileList", ".txt");
            String listContent = "file '" + tempFile1.getAbsolutePath() + "'\n" +
                    "file '" + tempFile2.getAbsolutePath() + "'\n";
            java.nio.file.Files.write(listFile.toPath(), listContent.getBytes());

            String outputFilePath = "output_merged.mp3";
            String ffmpegCommand = String.format(
                    "ffmpeg -f concat -safe 0 -i %s -c copy %s",
                    listFile.getAbsolutePath(), outputFilePath
            );

            ProcessBuilder processBuilder = new ProcessBuilder(ffmpegCommand.split(" "));
            processBuilder.inheritIO();  // Hiển thị output ra console
            Process process = processBuilder.start();
            process.waitFor();

            return "Audio files merged successfully. Output file: " + outputFilePath;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Error while merging audio.";
        }
    }

    @Override
    public String cutMedia(MultipartFile file, String startTime, String endTime,String fileExtension) {
        try {
            File tempFile = File.createTempFile("temp_media_", fileExtension);
            file.transferTo(tempFile);

            String outputFilePath = "output_" + tempFile.getName();
            String ffmpegCommand = String.format(
                    "ffmpeg -i %s -ss %s -to %s -c copy %s",
                    tempFile.getAbsolutePath(), startTime, endTime, outputFilePath
            );

            ProcessBuilder processBuilder = new ProcessBuilder(ffmpegCommand.split(" "));
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            process.waitFor();

            return "Media file cut successfully. Output file: " + outputFilePath;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Error while cutting media.";
        }
    }

    @Override
    public String mergeMedia(List<MultipartFile> files, String fileExtension) {
        try {
            if (files.isEmpty()) {
                return "No files provided for merging.";
            }
            if (!fileExtension.equals(".mp3") && !fileExtension.equals(".mp4")) {
                return "Unsupported file format. Only .mp3 and .mp4 are allowed.";
            }
            String resourceType = fileExtension.equals(".mp3") ? "audio" : "video";
            File listFile = File.createTempFile("fileList", ".txt");
            StringBuilder listContent = new StringBuilder();
            List<File> tempFiles = new ArrayList<>();

            for (MultipartFile file : files) {
                File tempFile = File.createTempFile("temp_media_", fileExtension);
                file.transferTo(tempFile);
                tempFiles.add(tempFile);
                listContent.append("file '").append(tempFile.getAbsolutePath()).append("'\n");
            }

            Files.write(listFile.toPath(), listContent.toString().getBytes());
            File outputFile = File.createTempFile("merged_", fileExtension);
            String ffmpegCommand = String.format(
                    "ffmpeg -y -f concat -safe 0 -i %s -c copy %s",
                    listFile.getAbsolutePath(), outputFile.getAbsolutePath()
            );

            ProcessBuilder processBuilder = new ProcessBuilder(ffmpegCommand.split(" "));
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            process.waitFor();
            MultipartFile multipartOutputFile = new MockMultipartFile(
                    outputFile.getName(),
                    outputFile.getName(),
                    "application/octet-stream",
                    Files.readAllBytes(outputFile.toPath())
            );

            String cloudinaryUrl = cloudinaryService.uploadFile(multipartOutputFile, resourceType);
            listFile.delete();
            for (File tempFile : tempFiles) {
                tempFile.delete();
            }
            outputFile.delete();

            Video newVideo = new Video();
            newVideo.setUrl(cloudinaryUrl);
            videoRepo.save(newVideo);

            return cloudinaryUrl != null ? cloudinaryUrl : "Error uploading merged file to Cloudinary.";
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Error while merging media.";
        }

    }



}
