package com.nat.cloudman.cloud;

import com.nat.cloudman.cloud.copy.InterCloudTask;
import com.nat.cloudman.model.Cloud;
import com.nat.cloudman.response.DownloadedFileContainer;
import com.nat.cloudman.response.FilesContainer;
import com.nat.cloudman.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CloudManagerFacade {

    @Autowired
    private UserService userService;

    @Autowired
    private DropboxManager dropboxManager;


    @Autowired
    private OneDriveManager oneDriveManager;


    @Autowired
    private UserManager userManager;

    private Map<String, InterCloudTask> interCloudTasks = new HashMap<>();

    private Map<String, CloudManager> cloudManagers = new HashMap<>();

    @Autowired
    public void setCloudManagers(List<CloudManager> cloudManagers) {
        for (CloudManager cloudManager : cloudManagers) {
            this.cloudManagers.put(cloudManager.getServiceName(), cloudManager);
        }
    }

    @Autowired
    public void setCopyTasks(List<InterCloudTask> tasks) {
        for (InterCloudTask task : tasks) {
            System.out.println("put task : " + task.getClass().getSimpleName());
            this.interCloudTasks.put(task.getClass().getSimpleName(), task);
        }
    }

    public FilesContainer getFilesList(String accountName, String folderPath) {
        Cloud cloud = userManager.getCloud(accountName);
        String cloudService = cloud.getCloudService();

        return cloudManagers.get(cloudService).getFilesList(accountName, folderPath);
    }

    public void uploadFile(String cloudName, File localFile, String pathToUpload) throws Exception {
        System.out.println("uploadFile(),");
        Cloud cloud = userManager.getCloud(cloudName);
        cloudManagers.get(cloud.getCloudService()).uploadFile(cloud, localFile, pathToUpload);
    }

    public void addFolder(String folderName, String cloudName, String path, String parentId) {
        Cloud cloud = userManager.getCloud(cloudName);
        cloudManagers.get(cloud.getCloudService()).addFolder(folderName, cloud, path, parentId);
    }

    private DownloadedFileContainer download(String fileName, String cloudName, String fileId, String path) {
        Cloud cloud = userManager.getCloud(cloudName);
        return cloudManagers.get(cloud.getCloudService()).download(fileName, fileId, path, cloud);
    }

    public ResponseEntity<InputStreamResource> downloadFile(String fileName, String cloudName, String fileId, String path) {
        DownloadedFileContainer fileContainer = download(fileName, cloudName, fileId, path);
        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentType(MediaType.parseMediaType("application/force-download"));
        byte[] arr = fileContainer.getByteArray();
        respHeaders.setContentLength(arr.length);
        respHeaders.setContentDispositionFormData("attachment", fileContainer.getName());
        InputStreamResource isr = new InputStreamResource(new ByteArrayInputStream(arr));
        return new ResponseEntity<InputStreamResource>(isr, respHeaders, HttpStatus.OK);
    }

    public void deleteFile(String fileName, String cloudName, String fileId, String path) {
        Cloud cloud = userManager.getCloud(cloudName);
        cloudManagers.get(cloud.getCloudService()).deleteFile(fileName, fileId, path, cloud);
    }

    public void renameFile(String fileName, String newName, String cloudName, String fileId, String path) {
        Cloud cloud = userManager.getCloud(cloudName);
        cloudManagers.get(cloud.getCloudService()).renameFile(fileName, fileId, newName, path, cloud);
    }

    public void copyFile(String cloudSourceName, String pathSource, String idSource, String downloadUrl, String cloudDestName, String pathDest, String idDest) {
        Cloud cloudSource = userManager.getCloud(cloudSourceName);
        InterCloudTask task = interCloudTasks.get(userManager.getCloud(cloudSourceName).getCloudService() + "To" + userManager.getCloud(cloudDestName).getCloudService());
        System.out.println("null:" + (task == null));
        System.out.println("name:" + task.getClass().getSimpleName());
        task.copyFile(cloudSourceName, pathSource, idSource, downloadUrl, cloudDestName, pathDest, idDest);

    }
}