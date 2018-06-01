package uk.gov.ons.fwmt.job_service.exceptions.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import uk.gov.ons.fwmt.job_service.data.dto.GatewayCommonErrorDTO;
import uk.gov.ons.fwmt.job_service.exceptions.ExceptionCode;
import uk.gov.ons.fwmt.job_service.exceptions.types.InvalidFileNameException;
import uk.gov.ons.fwmt.job_service.exceptions.types.MediaTypeNotSupportedException;
import uk.gov.ons.fwmt.job_service.exceptions.types.UnknownUserException;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalTime;

@ControllerAdvice
@Slf4j
public class RestExceptionHandler {
  public ResponseEntity<GatewayCommonErrorDTO> makeCommonError(HttpServletRequest request, Throwable exception,
      HttpStatus status, String error, String message) {
    GatewayCommonErrorDTO errorDTO = new GatewayCommonErrorDTO();
    errorDTO.setError(error);
    errorDTO.setException(exception.getClass().getName());
    errorDTO.setMessage(message);
    errorDTO.setPath(request.getContextPath());
    errorDTO.setStatus(status.value());
    errorDTO.setTimestamp(LocalTime.now().toString());
    return new ResponseEntity<>(errorDTO, status);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<GatewayCommonErrorDTO> handleAnyException(HttpServletRequest request, Exception exception) {
    log.error(ExceptionCode.FWMT_JOB_SERVICE_0001.toString()+" "+ExceptionCode.FWMT_JOB_SERVICE_0001.getError(), exception);
    return makeCommonError(request, exception, HttpStatus.INTERNAL_SERVER_ERROR, "Unknown error", "Unknown error");
  }

  @ExceptionHandler(MediaTypeNotSupportedException.class)
  public ResponseEntity<GatewayCommonErrorDTO> handleContentTypeException(HttpServletRequest request,
      HttpMediaTypeNotSupportedException exception) {
    log.error(ExceptionCode.FWMT_JOB_SERVICE_0003.toString()+" "+ExceptionCode.FWMT_JOB_SERVICE_0003.getError(), exception);
    return makeCommonError(request, exception, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid content type",
        exception.getMessage());
  }

  @ExceptionHandler(InvalidFileNameException.class)
  public ResponseEntity<GatewayCommonErrorDTO> handleInvalidFileNameException(HttpServletRequest request,
      InvalidFileNameException exception) {
    log.error(ExceptionCode.FWMT_JOB_SERVICE_0002.toString()+" "+ExceptionCode.FWMT_JOB_SERVICE_0002.getError(), exception);
    return makeCommonError(request, exception, HttpStatus.BAD_REQUEST, "Invalid CSV File Name", exception.toString());
  }

  @ExceptionHandler(UnknownUserException.class)
  public ResponseEntity<GatewayCommonErrorDTO> handleInvalidFileNameException(HttpServletRequest request,
      UnknownUserException exception) {
    log.error(ExceptionCode.FWMT_JOB_SERVICE_0010.toString()+" "+ExceptionCode.FWMT_JOB_SERVICE_0010.getError(), exception);
    return makeCommonError(request, exception, HttpStatus.INTERNAL_SERVER_ERROR, "Unknown user", exception.toString());
  }

}
