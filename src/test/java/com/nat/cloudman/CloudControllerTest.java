package com.nat.cloudman;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.jayway.jsonpath.JsonPath;
import com.nat.cloudman.controllers.params.FileParameters;
import com.nat.cloudman.controllers.params.FolderParameters;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
public class CloudControllerTest {

    private static final Logger logger = LoggerFactory.getLogger(CloudControllerTest.class);

    private MediaType contentType = new MediaType(MediaType.APPLICATION_JSON.getType(),
            MediaType.APPLICATION_JSON.getSubtype(),
            Charset.forName("utf8"));

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    List<TestParam> paramArr;

    @WithMockUser(username = "cltest5@outlook.com", roles = {"ADMIN"})
    @Before
    public void setup() throws Exception {
        logger.debug("test setup");
        this.mockMvc = webAppContextSetup(webApplicationContext).build();
        paramArr = new ArrayList<>();
        paramArr.add(new TestParam("", "Dropbox cloud", "dropbox folder test", "", "folder", "Dropbox"));
        paramArr.add(new TestParam("", "OD cloud", "od folder test", "3126D7302C73EA98!101", "folder", "OneDrive"));
        paramArr.add(new TestParam("", "GOOGLE!!", "google folder test", "0AOMJr8Ji_BXuUk9PVA", "folder", "Google Drive"));
    }

    @WithMockUser(username = "cltest5@outlook.com", roles = {"ADMIN"})
    @Test
    public void listFilesOkStatus() throws Exception {
        logger.debug("listFilesOkStatus");
        for (TestParam param : paramArr) {
            MvcResult result = mockMvc.perform(post("/listfiles")
                    .param("path", "")
                    .param("folderId", param.rootFolderId)
                    .param("cloudName", param.cloudName))
                    .andExpect(status().isOk())
                    .andReturn();
            String content = result.getResponse().getContentAsString();
            logger.debug("content: " + content + ", status: " + result.getResponse().getStatus());
        }
    }

    @WithMockUser(username = "cltest5@outlook.com", roles = {"ADMIN"})
    @Test
    public void uploadFileTest() throws Exception {
        logger.debug("uploadFileTest");
        for (TestParam param : paramArr) {
            FolderParameters params = new FolderParameters(param.path, param.cloudName, param.folderName, param.rootFolderId);
            assertTrue(addFolder(params));
            Map<String, Object> file = filePresents(param.path, param.rootFolderId, param.cloudName, param.folderName, "folder");
            assertNotNull(file);
            assertTrue(uploadSampleFile((String) file.get("pathLower"), (String) file.get("id"), param.cloudName));
            Map<String, Object> uploadedFile = filePresents((String) file.get("pathLower"), (String) file.get("id"), param.cloudName, "test.jpg", "file");
            assertNotNull(uploadedFile);
            assertEquals("test.jpg", (String) uploadedFile.get("name"));
            assertTrue(removeFile((String) file.get("id"), (String) file.get("pathLower"), param.cloudName, param.rootFolderId, param.folderName));
        }
    }

    @WithMockUser(username = "cltest5@outlook.com", roles = {"ADMIN"})
    @Test
    public void addFolderTest() throws Exception {
        logger.debug("test addFolder");
        for (TestParam param : paramArr) {
            FolderParameters params = new FolderParameters(param.path, param.cloudName, param.folderName, param.rootFolderId);
            assertTrue(addFolder(params));
            Map<String, Object> file = filePresents(param.path, param.rootFolderId, param.cloudName, param.folderName, "folder");
            assertNotNull(file);
            assertTrue(removeFile((String) file.get("id"), (String) file.get("pathLower"), param.cloudName, param.rootFolderId, param.folderName));
        }
    }

    @WithMockUser(username = "cltest5@outlook.com", roles = {"ADMIN"})
    @Test
    public void getClouds() throws Exception {
        logger.debug("test getClouds");
        ResultActions actions = mockMvc
                .perform(post("/getclouds"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(contentType))
                .andExpect(jsonPath("$.clouds", hasSize(paramArr.size())));
        for (TestParam param : paramArr) {
            logger.debug("cloud: " + param.cloudName);
            actions = actions.andExpect(jsonPath("$.clouds[?(@.accountName == '" + param.cloudName + "')].service").value(param.servise));
        }
    }

    public Map<String, Object> filePresents(String path, String folderId, String cloudName, String fileName, String fileType) throws Exception {
        logger.debug("filePresents, path: " + path + ", folderId: " + folderId +
                ", cloudName: " + cloudName + ", fileName: " + fileName + ", fileType: " + fileType);
        MvcResult result = mockMvc.perform(post("/listfiles")
                .param("path", path)
                .param("folderId", folderId)
                .param("cloudName", cloudName))
                .andReturn();
        String content = result.getResponse().getContentAsString();
        logger.debug("content: " + content + ", status: " + result.getResponse().getStatus());
        List<Map<String, Object>> dataList = JsonPath.parse(content)
                .read("$.files");
        for (int i = 0; i < dataList.size(); i++) {
            Map<String, Object> file = dataList.get(i);
            String name = (String) file.get("name");
            String type = (String) file.get("type");
            logger.debug("i: " + i + ", name: " + name + ", type: " + type);
            if (name.equals(fileName) && type.equals(fileType)) {
                return file;
            }
        }
        return null;
    }

    public boolean removeFile(String fileId, String path, String cloudName, String parentId, String fileName) throws Exception {
        logger.debug("removeFile: fileId: " + fileId + ", path: " + path + ", cloudName: " + cloudName + ", parentId: " + parentId);
        FileParameters params = new FileParameters(fileName, fileId, cloudName, path, "", parentId);
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String json = ow.writeValueAsString(params);
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.delete("/deletefile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andReturn();
        String content = result.getResponse().getContentAsString();
        logger.debug("content: " + content + ", status: " + result.getResponse().getStatus());
        if (result.getResponse().getStatus() == 200) {
            return true;
        }
        return false;
    }


    public boolean uploadSampleFile(String filePath, String parentId, String cloudName) throws Exception {
        logger.debug("uploadSampleFile filePath: " + filePath + ", parentId: " + parentId + ", cloudName: " + cloudName);
        if (filePath == null) {
            filePath = "";
        }
        FileInputStream fis = new FileInputStream("C:\\pics\\test.jpg");
        // !!! "files" is parameter name for files array
        MockMultipartFile multipartFile = new MockMultipartFile("files", "test.jpg", "image/jpeg", fis);
        // to sent few files
        // MockMultipartFile multipartFile2 = new MockMultipartFile("files", "file2.txt", "text/plain", "This document intended to be uploaded and attached to thread.".getBytes());
        logger.debug("multipartFile name: " + multipartFile.getName() + ", size: " + multipartFile.getSize());
        MvcResult result = mockMvc
                .perform
                        (MockMvcRequestBuilders.fileUpload("http://localhost:8080/upload")
                                .file(multipartFile)
                                // .file(multipartFile2)
                                .contentType(MediaType.MULTIPART_FORM_DATA)
                                .param("filePath", filePath)
                                .param("parentId", parentId)
                                .param("cloudName", cloudName))
                .andReturn();
        String content = result.getResponse().getContentAsString();
        logger.debug("content uploadSampleFile: " + content + ", status: " + result.getResponse().getStatus());
        if (result.getResponse().getStatus() == 200) {
            return true;
        }
        return false;
    }


    public boolean addFolder(FolderParameters params) throws Exception {
        logger.debug("addFolder");
        // params are in request body
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String json = ow.writeValueAsString(params);
        logger.debug("params: " + json);
        MvcResult result = mockMvc.perform(post("http://localhost:8080/addfolder")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andReturn();
        String content = result.getResponse().getContentAsString();
        int status = result.getResponse().getStatus();
        logger.debug(" content: " + content + ", status: " + status);
        if (status == 200) {
            return true;
        }
        return false;
    }

    private class TestParam {
        String path;
        String cloudName;
        String folderName;
        String rootFolderId;
        String fileType;
        String servise;

        public TestParam(String path, String cloudName, String folderName, String rootFolderId, String fileType, String servise) {
            this.path = path;
            this.cloudName = cloudName;
            this.folderName = folderName;
            this.rootFolderId = rootFolderId;
            this.fileType = fileType;
            this.servise = servise;
        }
    }


    @After
    public void cleanUp() throws Exception {
    }
}
