package de.neebs.franchise.control;

import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@ResponseStatus(BAD_REQUEST)
public class IllegalDrawException extends RuntimeException {
    public IllegalDrawException(String message) {
        super(message);
    }
}
