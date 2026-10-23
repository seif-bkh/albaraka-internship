package com.albaraka.backend.repository;

import com.albaraka.backend.model.NewsItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

    /** Newest first — the public/portal feed. */
    List<NewsItem> findByActiveTrueAndPublishedAtLessThanEqualOrderByPublishedAtDescIdDesc(LocalDate today);

    List<NewsItem> findAllByOrderByPublishedAtDescIdDesc();
}
