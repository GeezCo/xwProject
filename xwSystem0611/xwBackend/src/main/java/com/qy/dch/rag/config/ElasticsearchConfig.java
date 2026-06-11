package com.qy.dch.rag.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Autowired
    private RagProperties ragProperties;

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        RagProperties.Elasticsearch es = ragProperties.getEs();
        HttpHost host = new HttpHost(es.getHost(), es.getPort(), "http");

        RestClient restClient;
        if (es.getUsername() != null && !es.getUsername().isEmpty()) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(es.getUsername(), es.getPassword())
            );
            restClient = RestClient.builder(host)
                    .setHttpClientConfigCallback(httpClientBuilder ->
                            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                    .build();
        } else {
            restClient = RestClient.builder(host).build();
        }

        return new RestHighLevelClient(restClient);
    }
}
