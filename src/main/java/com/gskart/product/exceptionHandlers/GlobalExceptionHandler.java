package com.gskart.product.exceptionHandlers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<String> exceptionHandler(Exception exception){
        // ToDo Log the exception
        // Logging in console for now.
        System.out.println(exception.toString());
        exception.printStackTrace();
        return new ResponseEntity<>("Unexpected error occurred. Unable to process this request.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
