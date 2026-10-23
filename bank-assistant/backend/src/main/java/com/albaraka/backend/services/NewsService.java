package com.albaraka.backend.services;

import com.albaraka.backend.dto.NewsItemRequest;
import com.albaraka.backend.model.NewsItem;
import com.albaraka.backend.repository.NewsItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * News items (P2): staff CRUD in the back-office; the public/portal feed
 * serves only active rows whose publish date has been reached.
 */
@Service
public class NewsService {

    /** Default feed size if the caller does not bound it. */
    static final int DEFAULT_FEED_LIMIT = 3;
    static final int MAX_FEED_LIMIT = 12;

    private final NewsItemRepository news;

    public NewsService(NewsItemRepository news) {
        this.news = news;
    }

    @Transactional(readOnly = true)
    public List<NewsItem> publicFeed(Integer limit) {
        int bounded = limit == null ? DEFAULT_FEED_LIMIT : Math.max(1, Math.min(limit, MAX_FEED_LIMIT));
        return news.findByActiveTrueAndPublishedAtLessThanEqualOrderByPublishedAtDescIdDesc(LocalDate.now())
                .stream()
                .limit(bounded)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NewsItem> listAll() {
        return news.findAllByOrderByPublishedAtDescIdDesc();
    }

    @Transactional
    public NewsItem create(NewsItemRequest req) {
        NewsItem n = new NewsItem();
        apply(n, req);
        if (n.getExternalLink() != null) {
            validateLink(n.getExternalLink());
        }
        return news.save(n);
    }

    @Transactional
    public NewsItem update(Long id, NewsItemRequest req) {
        NewsItem n = news.findById(id).orElseThrow(NoSuchElementException::new);
        apply(n, req);
        if (n.getExternalLink() != null) {
            validateLink(n.getExternalLink());
        }
        return news.save(n);
    }

    @Transactional
    public NewsItem toggleActive(Long id) {
        NewsItem n = news.findById(id).orElseThrow(NoSuchElementException::new);
        n.setActive(!n.isActive());
        return news.save(n);
    }

    @Transactional
    public void delete(Long id) {
        NewsItem n = news.findById(id).orElseThrow(NoSuchElementException::new);
        news.delete(n);
    }

    private static void apply(NewsItem n, NewsItemRequest req) {
        n.setTitle(req.title().trim());
        n.setBody(req.body().trim());
        n.setExternalLink(trimOrNull(req.externalLink()));
        n.setPublishedAt(req.publishedAt());
        if (req.active() != null) {
            n.setActive(req.active());
        }
    }

    private static void validateLink(String link) {
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            throw new IllegalArgumentException("Le lien externe doit commencer par http:// ou https://.");
        }
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
