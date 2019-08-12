package com.idowran.aio;

import java.io.Closeable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamUtil {
	private static Logger log = LoggerFactory.getLogger(ReadCH.class);
	
	public static void close(Closeable p) {
		if(p == null) {
			return;
		}
		try {
			p.close();
		} catch (Exception e) {
			log.error("Error on close {}", p.getClass().getName(), e);
		}
	}
}
