package vn.edu.hust.collector.service;

import vn.edu.hust.collector.model.FetchedContent;

@FunctionalInterface
public interface Downloader {
    FetchedContent fetch(String url) throws Exception;
}
