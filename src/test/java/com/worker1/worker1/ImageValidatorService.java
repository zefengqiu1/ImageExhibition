package com.worker1.worker1;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class ImageValidatorService {

    public boolean isImageUrlValid(String imageUrl) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            return false;
        }
    }
}

