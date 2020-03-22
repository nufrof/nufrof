package com.nufrof.vcloud;

import com.nufrof.vcloud.domain.VAppRequest;
import com.nufrof.vcloud.domain.VMRequest;
import com.vmware.cxfrestclient.CxfClientSecurityContext;
import com.vmware.vcloud.api.rest.client.VcdBasicLoginCredentials;
import com.vmware.vcloud.api.rest.client.VcdClient;
import com.vmware.vcloud.api.rest.client.VcdClientImpl;
import com.vmware.vcloud.api.rest.client.constants.RestAdminConstants;
import com.vmware.vcloud.api.rest.schema.ovf.MsgType;
import com.vmware.vcloud.api.rest.schema.ovf.RASDType;
import com.vmware.vcloud.api.rest.schema.ovf.VirtualHardwareSectionType;
import com.vmware.vcloud.api.rest.schema_v1_5.*;
import com.vmware.vcloud.api.rest.version.ApiVersion;
import org.apache.bval.jsr.ApacheValidationProvider;
import org.apache.cxf.jaxrs.provider.JAXBElementTypedProvider;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class VCloudVMService {
    private static final Logger LOGGER = Logger.getLogger(VCloudVMService.class.getName());

    private VcdClient vcdClient;

    private JAXBElementTypedProvider jaxbElementTypedProvider = getJAXBElementTypedProvider();

    private ObjectFactory objectFactory = new ObjectFactory();

    private Validator validator;

    public VCloudVMService(String url, ApiVersion apiVersion, String orgName, String username, String password) throws Exception {
        this.vcdClient = getVcdClient(url, apiVersion, orgName, username, password);
        ValidatorFactory validatorFactory
                = Validation.byProvider(ApacheValidationProvider.class)
                .configure().buildValidatorFactory();
        this.validator = validatorFactory.getValidator();
    }

    private JAXBElementTypedProvider getJAXBElementTypedProvider() {
        JAXBElementTypedProvider jaxbElementTypedProvider = new JAXBElementTypedProvider();
        jaxbElementTypedProvider.setExtraClass(OBJECT_FACTORIES);
        return jaxbElementTypedProvider;
    }

    private final Class<?>[] OBJECT_FACTORIES = new Class<?>[]{
            com.vmware.vcloud.api.rest.schema.versioning.ObjectFactory.class,
            com.vmware.vcloud.api.rest.schema_v1_5.ObjectFactory.class,
            com.vmware.vcloud.api.rest.schema_v1_5.extension.ObjectFactory.class,
            com.vmware.vcloud.api.rest.schema.ovf.vmware.ObjectFactory.class,
            com.vmware.vcloud.api.rest.schema.ovf.ObjectFactory.class,
            com.vmware.vcloud.api.rest.schema.ovf.environment.ObjectFactory.class
    };

    private VcdClient getVcdClient(String url, ApiVersion apiVersion, String orgName, String username, String password) throws Exception {
        LOGGER.fine("Attempting to create the VcdClient.");
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, new SecureRandom());
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        VcdClient vcdClient = new VcdClientImpl(URI.create(url + "/api"), Arrays.asList(apiVersion), CxfClientSecurityContext.getCxfClientSecurityContext(sslSocketFactory, false));
        vcdClient.setCredentials(new VcdBasicLoginCredentials(username, orgName, password));
        LOGGER.fine("VcdClient created successfully.");
        return vcdClient;
    }

    private CatalogType getCatalog(String catalogName) {
        String catalogTypeHref = vcdClient.getLoggedInOrg().getLink().stream().filter(link -> link.getType().equals(CatalogType.CONTENT_TYPE + RestAdminConstants.MediaType.XML_FORMAT_SUFFIX) && link.getName().equals(catalogName)).map(link -> link.getHref()).findFirst().get();
        CatalogType catalog = vcdClient.getResource(URI.create(catalogTypeHref), CatalogType.class);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Calling GET on " + catalogTypeHref + " returned the following:\n" + jaxbElementToXml(objectFactory.createCatalog(catalog)));
        }
        return catalog;
    }

    private VAppTemplateType getVAppTemplate(CatalogType catalog, String vAppName) {
        // Get the catalog item.
        ReferenceType vappCatalogItem = catalog.getCatalogItems().getCatalogItem().stream().filter(catalogItem -> catalogItem.getName().equals(vAppName)).findFirst().get();
        CatalogItemType catalogItemType = vcdClient.getResource(URI.create(vappCatalogItem.getHref()), CatalogItemType.class);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Calling GET on " + vappCatalogItem.getHref() + " returned the following:\n" + jaxbElementToXml(objectFactory.createCatalogItem(catalogItemType)));
        }

        // Get the vAppTemplate.
        String entityHref = catalogItemType.getEntity().getHref();
        VAppTemplateType vAppTemplate = vcdClient.getResource(URI.create(entityHref), VAppTemplateType.class);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Calling GET on " + entityHref + " returned the following:\n" + jaxbElementToXml(objectFactory.createVAppTemplate(vAppTemplate)));
        }
        return vAppTemplate;
    }

    private ReferenceType getVmReference(VAppTemplateType vAppTemplate, String vmTemplateName, String vmName) {
        VAppTemplateType vmTemplate = vAppTemplate.getChildren().getVm().stream().filter(vm -> vm.getName().equals(vmTemplateName)).findFirst().get();
        ReferenceType vmReferenceType = new ReferenceType();
        vmReferenceType.setName(vmName);
        vmReferenceType.setHref(vmTemplate.getHref());
        return vmReferenceType;
    }

    private NetworkConnectionSectionType getVMNetworkSection(String networkName, String ipAddressAllocationMode, Boolean isConnected) {
        // Get the network connection.
        NetworkConnectionType networkConnectionType = new NetworkConnectionType();
        networkConnectionType.setNetwork(networkName);
        networkConnectionType.setIpAddressAllocationMode(ipAddressAllocationMode);
        networkConnectionType.setIsConnected(isConnected);

        // Get the network connection section.
        NetworkConnectionSectionType networkConnectionSectionType = new NetworkConnectionSectionType();
        networkConnectionSectionType.getNetworkConnection().add(networkConnectionType);
        networkConnectionSectionType.setInfo(new MsgType());
        return networkConnectionSectionType;
    }

    private SourcedCompositionItemParamType getSourceVm(VAppTemplateType vAppTemplate, NetworkConnectionSectionType vmNetworkSection, String vmTemplateName, String vmName) {
        // From the template, get the populated reference to the vm.
        ReferenceType vmReferenceType = getVmReference(vAppTemplate, vmTemplateName, vmName);

        // Get vm instantiation params.
        InstantiationParamsType vmInstantiationParamsType = new InstantiationParamsType();
        vmInstantiationParamsType.getSection().add(objectFactory.createNetworkConnectionSection(vmNetworkSection));

        // Create vm request item.
        SourcedCompositionItemParamType sourcedCompositionItemParamType = new SourcedCompositionItemParamType();
        sourcedCompositionItemParamType.setSource(vmReferenceType);
        sourcedCompositionItemParamType.setInstantiationParams(vmInstantiationParamsType);

        // Return the vm request item.
        return sourcedCompositionItemParamType;
    }

    private NetworkConfigSectionType getVappNetworkConfig(String networkName) {
        // Get the parent network for the org.
        LinkType networkQueryLinkType = vcdClient.getQueryList().getLink().stream().filter(link -> link.getHref().contains("orgVdcNetwork") && link.getType().contains("references")).findFirst().get();
        ReferencesType orgVdcNetwork = vcdClient.getResource(URI.create(networkQueryLinkType.getHref()), ReferencesType.class);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Calling GET on " + networkQueryLinkType.getHref() + " returned the following:\n" + jaxbElementToXml(objectFactory.createReferences(orgVdcNetwork)));
        }
        JAXBElement<ReferenceType> jaxbElement = orgVdcNetwork.getReference().stream().filter(network -> network.getValue().getName().equals(networkName)).findFirst().get();
        ReferenceType parentNetwork = jaxbElement.getValue();

        // Create a network configuration.
        NetworkConfigurationType networkConfigurationType = new NetworkConfigurationType();
        networkConfigurationType.setParentNetwork(parentNetwork);
        networkConfigurationType.setFenceMode("bridged");

        // Create a vapp network configuration.
        VAppNetworkConfigurationType vAppNetworkConfigurationType = new VAppNetworkConfigurationType();
        vAppNetworkConfigurationType.setConfiguration(networkConfigurationType);
        vAppNetworkConfigurationType.setNetworkName(networkName);

        // Get the networking configs portion of the request body.
        NetworkConfigSectionType networkConfigSectionType = new NetworkConfigSectionType();
        networkConfigSectionType.getNetworkConfig().add(vAppNetworkConfigurationType);
        networkConfigSectionType.setInfo(new MsgType());
        return networkConfigSectionType;
    }

    private ComposeVAppParamsType getVappCompositionRequestBody(NetworkConfigSectionType vappNetworkSection, String vAppName, List<SourcedCompositionItemParamType> sourceVms, boolean deploy, boolean powerOn) {
        // Get vapp isntantiation params.
        InstantiationParamsType composeVAPPInstantiationParamsType = new InstantiationParamsType();
        composeVAPPInstantiationParamsType.getSection().add(objectFactory.createNetworkConfigSection(vappNetworkSection));

        // Get compose request body.
        ComposeVAppParamsType composeVAppParamsType = new ComposeVAppParamsType();
        composeVAppParamsType.setName(vAppName);
        composeVAppParamsType.setDescription("Generated programmatically.");
        composeVAppParamsType.setDeploy(deploy);
        composeVAppParamsType.setPowerOn(powerOn);
        composeVAppParamsType.setInstantiationParams(composeVAPPInstantiationParamsType);
        composeVAppParamsType.getSourcedItem().addAll(sourceVms);

        return composeVAppParamsType;
    }

    private VdcType getVdcType(String orgName) {
        // Get the org.
        String vdcHref = vcdClient.getLoggedInOrg().getLink().stream().filter(link -> link.getName().equals(orgName)).map(link -> link.getHref()).findFirst().get();

        // Get the vdc.
        VdcType vdcType = vcdClient.getResource(URI.create(vdcHref), VdcType.class);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Calling GET on " + vdcHref + " returned the following:\n" + jaxbElementToXml(objectFactory.createVdc(vdcType)));
        }

        return vdcType;
    }

    private VAppType requestComposition(VdcType vdc, ComposeVAppParamsType vappCompositionRequestBody) {
        // Get the compose vapp link.
        LinkType linkType = vdc.getLink().stream().filter(link -> link.getType().equals(ComposeVAppParamsType.CONTENT_TYPE + RestAdminConstants.MediaType.XML_FORMAT_SUFFIX)).findFirst().get();

        // Request vapp composition.
        LOGGER.fine("Requesting VApp composition.");
        JAXBElement<ComposeVAppParamsType> composeVAppParamsTypeJAXBElement = objectFactory.createComposeVAppParams(vappCompositionRequestBody);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("POSTing to " + linkType.getHref() + " the following:\n" + jaxbElementToXml(composeVAppParamsTypeJAXBElement));
        }
        VAppType vAppType = vcdClient.postResource(linkType, ComposeVAppParamsType.CONTENT_TYPE + RestAdminConstants.MediaType.XML_FORMAT_SUFFIX, composeVAppParamsTypeJAXBElement, VAppType.class);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Calling POST on " + linkType.getHref() + " returned the following:\n" + jaxbElementToXml(objectFactory.createVApp(vAppType)));
        }
        return vAppType;
    }

    private void validate(VAppRequest vAppRequest) throws Exception {
        Set<ConstraintViolation<VAppRequest>> constraintViolations = validator.validate(vAppRequest);
        if (constraintViolations.size() > 0) {
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append("The following constraints failed:\n");
            for (ConstraintViolation<VAppRequest> constraintViolation : constraintViolations) {
                stringBuffer.append("[Field]: " + constraintViolation.getPropertyPath() + ", [Value]: " + constraintViolation.getInvalidValue() + ", [Message]: " + constraintViolation.getMessage() + "\n");
            }
            throw new IllegalArgumentException(stringBuffer.toString());
        }
    }

    private List<SourcedCompositionItemParamType> getSourceVms(String networkName, List<VMRequest> vmRequests) {
        // Get all the VM's networking information.
        NetworkConnectionSectionType vmNetworkSection = getVMNetworkSection(networkName, "POOL", true);
        // Instantiate return object.
        List<SourcedCompositionItemParamType> sourceVms = new ArrayList<>();
        // Create a map of catalogs to VMRequests.  This way we only go over the wire and pull the catalog once.
        Map<String, List<VMRequest>> vmRequestByCatalog = vmRequests.stream().collect(Collectors.groupingBy(vm -> vm.getCatalog()));
        // Iterate over the catalogs...
        for (String catalogName : vmRequestByCatalog.keySet()) {
            // Get the catalog that holds the vapp.
            CatalogType catalog = getCatalog(catalogName);
            // Create a map of VApps to VMRequests.  This way we only go over the wire and pull the VAppTemplate once.
            Map<String, List<VMRequest>> vmRequestByVApp = vmRequestByCatalog.get(catalogName).stream().collect(Collectors.groupingBy(vm -> vm.getVApp()));
            // Iterate over the VAppTemplates...
            for (String vAppTemplateName : vmRequestByVApp.keySet()) {
                // Get the vapp's template.
                VAppTemplateType vAppTemplate = getVAppTemplate(catalog, vAppTemplateName);
                //Iterate over the VMRequests...
                for (VMRequest vmRequest : vmRequestByVApp.get(vAppTemplateName)) {
                    // Get a list of source VMs with networking information populated.
                    SourcedCompositionItemParamType sourceVm = getSourceVm(vAppTemplate, vmNetworkSection, vmRequest.getVm(), vmRequest.getUniqueName());
                    sourceVms.add(sourceVm);
                }
            }
        }
        return sourceVms;
    }

    public VAppType createVApp(VAppRequest vAppRequest) throws Exception {
        VAppType vAppType = composeVApp(vAppRequest, false, false);
        reconfigureVms(vAppType, vAppRequest);
        powerOn(vAppType, 5);
        return vAppType;
    }

    public VAppType composeVApp(VAppRequest vAppRequest, boolean deploy, boolean powerOn) throws Exception {
        // Validate the request object.
        validate(vAppRequest);

        // Get a list of source VMs with networking information populated.
        List<SourcedCompositionItemParamType> sourceVms = getSourceVms(vAppRequest.getNetwork(), vAppRequest.getVms());

        // Get all the vapp's networking information.
        NetworkConfigSectionType vappNetworkSection = getVappNetworkConfig(vAppRequest.getNetwork());

        // Get composition request body.
        ComposeVAppParamsType vappCompositionRequestBody = getVappCompositionRequestBody(vappNetworkSection, vAppRequest.getName(), sourceVms, deploy, powerOn);

        // Get the VDC.
        VdcType vdc = getVdcType(vAppRequest.getOrg());

        // Request composition.
        VAppType vAppType = requestComposition(vdc, vappCompositionRequestBody);

        // Wait for the composition to complete and refresh the in memory vAppType.
        vAppType = waitForCompositionAndRefresh(vAppType);

        return vAppType;
    }

    private VAppType waitForCompositionAndRefresh(VAppType vAppType) throws Exception {
        // Wait for composition to complete.
        LOGGER.info(vAppType.getTasks().getTask().size() + " tasks running.  Grabbing the first.");
        TaskType composeTaskType = vAppType.getTasks().getTask().get(0);
        waitUntilTaskComplete(composeTaskType, 5000);

        // Refresh vAppType so that so that it has all the now populated VM info.
        String vAppHref = vAppType.getHref();
        vAppType = vcdClient.getResource(URI.create(vAppHref), VAppType.class);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Calling GET on " + vAppHref + " returned the following:\n" + jaxbElementToXml(objectFactory.createVApp(vAppType)));
        }
        return vAppType;
    }

    public void undeployVApp(String vAppTypeHref) throws Exception {
        VAppType vAppType = vcdClient.getResource(URI.create(vAppTypeHref), VAppType.class);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Calling GET on " + vAppTypeHref + " returned the following:\n" + jaxbElementToXml(objectFactory.createVApp(vAppType)));
        }
        LinkType undeployLinkType = vAppType.getLink().stream().filter(link -> link.getRel().equals("undeploy")).findFirst().get();
        UndeployVAppParamsType undeployVAppParamsType = new UndeployVAppParamsType();
        undeployVAppParamsType.setUndeployPowerAction("powerOff");
        JAXBElement<UndeployVAppParamsType> undeployVAppParamsTypeJAXBElement = objectFactory.createUndeployVAppParams(undeployVAppParamsType);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("POSTing to " + undeployLinkType.getHref() + " the following:\n" + jaxbElementToXml(undeployVAppParamsTypeJAXBElement));
        }
        TaskType undeployTaskType = vcdClient.postResource(undeployLinkType, undeployLinkType.getType(), undeployVAppParamsTypeJAXBElement, TaskType.class);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Calling POST on " + undeployLinkType.getHref() + " returned the following:\n" + jaxbElementToXml(objectFactory.createTask(undeployTaskType)));
        }
        waitUntilTaskComplete(undeployTaskType, 1000);
    }

    public void removeVApp(String vAppTypeHref) throws Exception {
        // We repull the vAppType because the remove link isn't added until the vApp is undeployed.
        VAppType vAppType = vcdClient.getResource(URI.create(vAppTypeHref), VAppType.class);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Calling GET on " + vAppTypeHref + " returned the following:\n" + jaxbElementToXml(objectFactory.createVApp(vAppType)));
        }

        LinkType removeLinkType = vAppType.getLink().stream().filter(link -> link.getRel().equals("remove")).findFirst().get();
        TaskType removeTaskType = vcdClient.deleteResource(removeLinkType, true, true, TaskType.class);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Calling DELETE on " + removeLinkType.getHref() + " returned the following:\n" + jaxbElementToXml(objectFactory.createTask(removeTaskType)));
        }

        waitUntilTaskComplete(removeTaskType, 1000);

    }

    public void reconfigureVms(VAppType vAppType, VAppRequest vAppRequest) throws Exception {
        Map<String, List<VMRequest>> mapOfNamesToVMRequests = vAppRequest.getVms().stream().collect(Collectors.groupingBy(vm -> vm.getUniqueName()));
        for (VmType vmType : vAppType.getChildren().getVm()) {
            // Get the VMRequest.
            List<VMRequest> vmRequests = mapOfNamesToVMRequests.get(vmType.getName());
            if (vmRequests.size() != 1) {
                throw new RuntimeException("There should be exactly 1 VM per unique name, but there are " + vmRequests.size() + "!");
            }
            VMRequest vmRequest = vmRequests.get(0);

            // Update guest customization specs for the VM.
            updateGuestCustomizationSectionType(vmType, vmType.getName());

            // Update hardware specs for the VM.
            updateVirtualHardwareSectionType(vmType, vmRequest.getNumCpus(), vmRequest.getMbsMemory());

            // Get the request body for the reconfigure request.
            JAXBElement<VmType> vmTypeJAXBElement = objectFactory.createVm(vmType);

            // Get the reconfigure link, make the request, and wait for the task to complete.
            LinkType reconfigureLink = vmType.getLink().stream().filter(link -> link.getRel().equals("reconfigureVm")).findFirst().get();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("POSTing to " + reconfigureLink.getHref() + " the following:\n" + jaxbElementToXml(vmTypeJAXBElement));
            }
            TaskType reconfigureTaskType = vcdClient.postResource(URI.create(reconfigureLink.getHref()), reconfigureLink.getType(), vmTypeJAXBElement, TaskType.class);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Calling POST on " + reconfigureLink.getHref() + " returned the following:\n" + jaxbElementToXml(objectFactory.createTask(reconfigureTaskType)));
            }

            waitUntilTaskComplete(reconfigureTaskType, 1000);
        }
    }

    private void updateVirtualHardwareSectionType(VmType vm, Integer numCPUs, Integer memoryInMbs) {
        // Update the VM Sections to have the correct specs for...
        VirtualHardwareSectionType virtualHardwareSectionType = (VirtualHardwareSectionType) vm.getSection().stream().filter(section -> section.getDeclaredType().isAssignableFrom(VirtualHardwareSectionType.class)).findFirst().get().getValue();
        // ... Number of CPUs.
        RASDType numCpus = virtualHardwareSectionType.getItem().stream().filter(item -> item.getDescription().getValue().toLowerCase().contains("virtual cpus")).findFirst().get();
        numCpus.getVirtualQuantity().setValue(BigInteger.valueOf(numCPUs));
        // ... MBs of memory.
        RASDType memory = virtualHardwareSectionType.getItem().stream().filter(item -> item.getDescription().getValue().toLowerCase().contains("memory size")).findFirst().get();
        memory.getVirtualQuantity().setValue(BigInteger.valueOf(memoryInMbs));
    }

    private void updateGuestCustomizationSectionType(VmType vm, String vmName) {
        GuestCustomizationSectionType guestCustomizationSectionType = (GuestCustomizationSectionType) vm.getSection().stream().filter(section -> section.getDeclaredType().isAssignableFrom(GuestCustomizationSectionType.class)).findFirst().get().getValue();
        guestCustomizationSectionType.setComputerName(vmName);
    }

    public void powerOn(VAppType vAppType, Integer maxAttempts) {
        String powerOnHref = vAppType.getLink().stream().filter(link -> link.getRel().equals("power:powerOn")).findFirst().get().getHref();
        Integer powerOnAttempts = 0;
        while (powerOnAttempts < maxAttempts) {
            TaskType powerOnTaskType = null;
            try {
                powerOnTaskType = vcdClient.postResource(URI.create(powerOnHref), null, null, TaskType.class);
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("Calling POST on " + powerOnHref + " returned the following:\n" + jaxbElementToXml(objectFactory.createTask(powerOnTaskType)));
                }
                powerOnAttempts += 1;
                waitUntilTaskComplete(powerOnTaskType, 1000);
            } catch (Exception e) {
                if (powerOnAttempts < maxAttempts) {
                    LOGGER.log(Level.WARNING, "Attempt " + powerOnAttempts + " of power on failed.  Retrying.", e);
                } else {
                    LOGGER.log(Level.SEVERE, "Could not power on VMs.", e);
                    throw new RuntimeException("After " + powerOnAttempts + " attempts, could not power on VMs.");
                }
            }
        }
    }

    private void waitUntilTaskComplete(TaskType taskType, long msWaitInBetweenChecks) throws Exception {
        String taskHref = taskType.getHref();
        boolean complete = false;
        while (!complete) {
            taskType = vcdClient.getResource(URI.create(taskHref), TaskType.class);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Calling GET on " + taskHref + " returned the following:\n" + jaxbElementToXml(objectFactory.createTask(taskType)));
            }
            String status = taskType.getStatus();
            if (status.equals("error")) {
                throw new RuntimeException(taskType.getDetails());
            } else if (status.equals("success")) {
                complete = true;
            } else {
                LOGGER.info("Task " + taskType.getOperationName() + " is not yet complete.  Current status is " + status + ".");
            }
            Thread.sleep(msWaitInBetweenChecks);
        }
    }

    public String jaxbElementToXml(JAXBElement element) {
        try {
            JAXBContext jc = jaxbElementTypedProvider.getJAXBContext(element.getValue().getClass(), element.getDeclaredType());
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            marshaller.marshal(element, baos);
            return baos.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
