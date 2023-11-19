package org.openlca.app.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.google.gson.Gson;

public class Req {
	private static HttpClient client;
	private static Gson gson = new Gson();
	// beta
	// private final String base = "https://pre-api.gtech.world";
	// prod
	private static final String base = "https://api-v2.gtech.world";

	public static <T> T httpGET(String path, Class<T> classz) throws Exception {
		var req = HttpRequest.newBuilder(URI.create(base + path)).build();
		var res = instanceClinet().send(req, HttpResponse.BodyHandlers.ofString());
		return gson.fromJson(res.body(), classz);
	}
	
	public static <T> T httpPost(String path,String body, Class<T> classz) throws Exception {
		var bodyPub = HttpRequest.BodyPublishers.ofString(body);
		var req = HttpRequest.newBuilder(URI.create(base + path))
				.header("Content-Type", "application/json;charset=utf-8")
				.POST(bodyPub)
				.build();
		var res = instanceClinet().send(req, HttpResponse.BodyHandlers.ofString());
		return gson.fromJson(res.body(), classz);
	}

	public static class RES<T> {
		public int status;
		public String message;
		public T data;
		public RES(){
			
		}
	}
	
	private static HttpClient instanceClinet() {
		if (client == null) {
			client = HttpClient.newHttpClient();
		}
		return client;
	}
}
