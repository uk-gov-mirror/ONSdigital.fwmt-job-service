package uk.gov.ons.fwmt.job_service.service.impl.totalmobile;

import com.consiliumtechnologies.schemas.mobile._2009._03.visitstypes.AdditionalPropertyCollectionType;
import com.consiliumtechnologies.schemas.mobile._2009._03.visitstypes.AdditionalPropertyType;
import com.consiliumtechnologies.schemas.mobile._2015._05.optimisemessages.CreateJobRequest;
import com.consiliumtechnologies.schemas.mobile._2015._05.optimisemessages.UpdateJobHeaderRequest;
import com.consiliumtechnologies.schemas.mobile._2015._05.optimisetypes.*;
import com.consiliumtechnologies.schemas.services.mobile._2009._03.messaging.SendCreateJobRequestMessage;
import com.consiliumtechnologies.schemas.services.mobile._2009._03.messaging.SendMessageRequestInfo;
import com.consiliumtechnologies.schemas.services.mobile._2009._03.messaging.SendUpdateJobHeaderRequestMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.PropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.stereotype.Service;
import uk.gov.ons.fwmt.job_service.data.annotation.JobAdditionalProperty;
import uk.gov.ons.fwmt.job_service.data.legacy_ingest.LegacySampleIngest;
import uk.gov.ons.fwmt.job_service.service.totalmobile.TMJobConverterService;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class TMJobConverterServiceImpl implements TMJobConverterService {
  protected static final String JOB_QUEUE = "\\OPTIMISE\\INPUT";
  protected static final String JOB_SKILL = "Survey";
  protected static final String JOB_WORK_TYPE = "SS";
  protected static final String JOB_WORLD = "Default";

  private final DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
  private final ObjectFactory factory = new ObjectFactory();

  public TMJobConverterServiceImpl() throws DatatypeConfigurationException {
  }

  protected static void addAdditionalProperty(CreateJobRequest request, String key, String value) {
    AdditionalPropertyType propertyType = new AdditionalPropertyType();
    propertyType.setName(key);
    propertyType.setValue(value);
    request.getJob().getAdditionalProperties().getAdditionalProperty().add(propertyType);
  }

  /**
   * Read the JobAdditionalProperty annotations on the class T and set additional properties on the TM request
   */
  private static <T> void setFromAdditionalPropertyAnnotations(T instance, CreateJobRequest request) {
    Class<?> tClass = instance.getClass();
    PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(instance);
    for (Field field : tClass.getDeclaredFields()) {
      Optional<Annotation> annotation = Arrays.stream(field.getDeclaredAnnotations())
          .filter(an -> an.annotationType() == JobAdditionalProperty.class)
          .findFirst();
      if (annotation.isPresent()) {
        JobAdditionalProperty jobAdditionalProperty = (JobAdditionalProperty) annotation.get();
        Object value = accessor.getPropertyValue(field.getName());
        if (value != null) {
          addAdditionalProperty(request, jobAdditionalProperty.value(), value.toString());
        } else {
          log.warn("Unprocessed job property: " + field.getName());
          addAdditionalProperty(request, jobAdditionalProperty.value(), "");
        }
      }
    }
  }

  protected CreateJobRequest createJobRequestFromIngest(LegacySampleIngest ingest, String username) {
    // Setup the request object and all inner objects
    CreateJobRequest request = new CreateJobRequest();
    JobType job = new JobType();
    request.setJob(job);
    job.setLocation(new LocationType());
    job.setIdentity(new JobIdentityType());
    job.getLocation().setAddressDetail(new AddressDetailType());
    job.getLocation().getAddressDetail().setLines(new AddressDetailType.Lines());
    job.setContact(new ContactInfoType());
    job.setAttributes(new NameValueAttributeCollectionType());
    job.setAllocatedTo(new ResourceIdentityType());
    job.setSkills(new SkillCollectionType());
    job.setAdditionalProperties(new AdditionalPropertyCollectionType());
    job.setWorld(new WorldIdentityType());

    // Set the job id
    request.getJob().getIdentity().setReference(ingest.getTmJobId());

    // Set the job location
    LocationType location = request.getJob().getLocation();
    List<String> addressLines = location.getAddressDetail().getLines().getAddressLine();
    addressLines.add(ingest.getAddressLine1());
    addressLines.add(ingest.getAddressLine2());
    addressLines.add(ingest.getAddressLine3());
    addressLines.add(ingest.getAddressLine4());
    addressLines.add(ingest.getDistrict());
    addressLines.add(ingest.getPostTown());
    location.getAddressDetail().setPostCode(ingest.getPostcode());
    location.setReference(ingest.getSerNo());

    // Set the job contact
    request.getJob().getContact().setName(ingest.getPostcode());

    // Set the job skills
    request.getJob().getSkills().getSkill().add(JOB_SKILL);

    // Set the job work type
    request.getJob().setWorkType(JOB_WORK_TYPE);

    // Set the job world
    request.getJob().getWorld().setReference(JOB_WORLD);

    // Set the job due date
    GregorianCalendar dueDateCalendar = GregorianCalendar.from(ingest.getDueDate().atTime(23, 59, 59).atZone(ZoneId.of("UTC")));
    request.getJob().setDueDate(datatypeFactory.newXMLGregorianCalendar(dueDateCalendar));

    // Set the job description
    request.getJob().setDescription(ingest.getTla());

    // Set the allocated interviewer
    request.getJob().getAllocatedTo().setUsername(username);

    // additional properties
    setFromAdditionalPropertyAnnotations(ingest, request);
    switch (ingest.getLegacySampleSurveyType()) {
    case GFF:
      // TODO does splitSampleType need extra mapping?
      setFromAdditionalPropertyAnnotations(ingest.getGffData(), request);
      break;
    case LFS:
      setFromAdditionalPropertyAnnotations(ingest.getLfsData(), request);
      break;
    }

    // Set other required values
    request.getJob().setDuration(1);
    request.getJob().setVisitComplete(false);
    request.getJob().setDispatched(false);
    request.getJob().setAppointmentPending(false);
    request.getJob().setEmergency(false);

    return request;
  }

  protected UpdateJobHeaderRequest makeUpdateJobHeaderRequest(String tmJobId, String username) {
    UpdateJobHeaderRequest request = new UpdateJobHeaderRequest();
    request.setJobHeader(new JobHeaderType());
    request.getJobHeader().setAllocatedTo(new ResourceIdentityType());
    request.getJobHeader().setJobIdentity(new JobIdentityType());

    request.getJobHeader().getAllocatedTo().setUsername(username);
    request.getJobHeader().getJobIdentity().setReference(tmJobId);

    return request;
  }

  protected SendMessageRequestInfo makeSendMessageRequestInfo(String key) {
    SendMessageRequestInfo info = new SendMessageRequestInfo();
    info.setQueueName(JOB_QUEUE);
    info.setKey(key);
    return info;
  }

  public SendCreateJobRequestMessage createJob(LegacySampleIngest ingest, String username) {
    CreateJobRequest request = createJobRequestFromIngest(ingest, username);

    SendCreateJobRequestMessage message = new SendCreateJobRequestMessage();
    message.setSendMessageRequestInfo(makeSendMessageRequestInfo(ingest.getTmJobId()));
    message.setCreateJobRequest(request);

    return message;
  }

  public SendUpdateJobHeaderRequestMessage updateJob(String tmJobId, String username) {
    UpdateJobHeaderRequest request = makeUpdateJobHeaderRequest(tmJobId, username);

    SendUpdateJobHeaderRequestMessage message = new SendUpdateJobHeaderRequestMessage();
    message.setSendMessageRequestInfo(makeSendMessageRequestInfo(tmJobId));
    message.setUpdateJobHeaderRequest(request);

    return message;
  }

  public SendUpdateJobHeaderRequestMessage updateJob(LegacySampleIngest ingest, String username) {
    return updateJob(ingest.getTmJobId(), username);
  }

  public SendCreateJobRequestMessage createReissue(LegacySampleIngest ingest, String username) {
    return createJob(ingest, username);
  }
}

