package com.albaraka.backend.controller;

import com.albaraka.backend.dto.NewsItemResponse;
import com.albaraka.backend.services.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public feed for the landing page (no login — the landing is anonymous).
 * Serves only active items whose publish date has been reached, newest first.
 */
@RestController
@RequestMapping("/api/news")
public class NewsPublicController {

    private final NewsService service;

    public NewsPublicController(NewsService service) {
        this.service = service;
    }

    @GetMapping("/active")
    public List<NewsItemResponse> active(@RequestParam(required = false) Integer limit) {
        return service.publicFeed(limit).stream().map(NewsItemResponse::from).toList();
    }
}
