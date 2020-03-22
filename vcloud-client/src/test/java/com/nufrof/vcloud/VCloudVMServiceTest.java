package com.nufrof.vcloud;

import com.nufrof.vcloud.domain.VAppRequest;
import com.nufrof.vcloud.domain.VMRequest;
import com.vmware.vcloud.api.rest.schema_v1_5.VAppType;
import com.vmware.vcloud.api.rest.version.ApiVersion;
import org.jasypt.util.text.AES256TextEncryptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;

public class VCloudVMServiceTest {
    private static final String encryptionPassword = System.getenv("ENC_PWD");

    private static Properties properties;

    private static AES256TextEncryptor textEncryptor;

    @BeforeAll
    public static void beforeAll() throws Exception {
        properties = new Properties();
        properties.load(new FileReader(new File("src/test/resources/local.properties")));
        textEncryptor = new AES256TextEncryptor();
        textEncryptor.setPassword(encryptionPassword);
    }

    @Test
    public void doTest() throws Exception {
        VCloudVMService vCloudVMService = new VCloudVMService(properties.getProperty("url"), ApiVersion.VERSION_30_0, properties.getProperty("org"), properties.getProperty("username"), textEncryptor.decrypt(properties.getProperty("password")));
        VAppRequest vAppRequest = VAppRequest.builder()
                .org(properties.getProperty("org"))
                .network(properties.getProperty("network"))
                .vm(new VMRequest(properties.getProperty("catalog.1"), properties.getProperty("vapp.1"), properties.getProperty("vm.1"), "vm1", 2, 2048))
                .vm(new VMRequest(properties.getProperty("catalog.1"), properties.getProperty("vapp.1"), properties.getProperty("vm.1"), "vm2", 4, 2048))
                .vm(new VMRequest(properties.getProperty("catalog.2"), properties.getProperty("vapp.2"), properties.getProperty("vm.2"), "vm3", 2, 4096))
                .name("testvapp")
                .build();
        VAppType vAppType = vCloudVMService.createVApp(vAppRequest);
    }

    @Test
    public void encryptTest() {
        AES256TextEncryptor textEncryptor = new AES256TextEncryptor();
        textEncryptor.setPassword(encryptionPassword);
        String myEncryptedText = textEncryptor.encrypt("password");
        String plainText = textEncryptor.decrypt(myEncryptedText);
    }
}
