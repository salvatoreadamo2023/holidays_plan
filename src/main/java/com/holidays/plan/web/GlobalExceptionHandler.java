package com.holidays.plan.web;

import com.holidays.plan.service.ApiException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/** Non restituisce mai dettagli interni (stacktrace, SQL) al client. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(status.getReasonPhrase());
        return p;
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException e) {
        return problem(e.status(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, msg.isEmpty() ? "Dati non validi" : msg);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class})
    ProblemDetail unreadable(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Dati non validi");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Operazione non consentita");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail lock(Exception e) {
        return problem(HttpStatus.CONFLICT, "La richiesta e' stata modificata da qualcun altro, ricarica la pagina");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Dato duplicato o in uso");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail notFound(NoResourceFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Non trovato");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail generic(Exception e) {
        log.error("Errore non gestito", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Errore interno, riprova piu' tardi");
    }
}
