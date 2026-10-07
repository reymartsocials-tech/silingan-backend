package com.ria.olita.tech.silingan.exception;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ForbiddenException extends ServiceException {

	public ForbiddenException(String message) {
		super("FORBIDDEN", message);
		log.error("ForbiddenException: {}", message);
	}

}
