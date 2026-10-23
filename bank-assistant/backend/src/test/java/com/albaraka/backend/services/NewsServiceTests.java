package com.albaraka.backend.services;

import com.albaraka.backend.dto.NewsItemRequest;
import com.albaraka.backend.model.NewsItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * News feed rules (P2): only active, already-published items reach clients;
 * staff CRUD works; external links must be http(s).
 */
@SpringBootTest
@Transactional
class NewsServiceTests {

    @Autowired NewsService service;

    private NewsItemRequest req(String title, LocalDate publishedAt) {
        return new NewsItemRequest(title, "Corps de l'article.", "https://albarakabank.com.tn/x",
                publishedAt, null);
    }

    @Test
    void publicFeedServesOnlyActivePublishedItemsNewestFirst() {
        NewsItem old = service.create(req("Ancienne", LocalDate.now().minusDays(2)));
        NewsItem new1 = service.create(req("Nouvelle", LocalDate.now()));
        service.create(req("Future", LocalDate.now().plusDays(1)));

        List<NewsItem> feed = service.publicFeed(null);
        assertThat(feed).extracting(NewsItem::getTitle)
                .containsExactly("Nouvelle", "Ancienne");

        service.toggleActive(new1.getId());
        assertThat(service.publicFeed(null)).extracting(NewsItem::getTitle)
                .containsExactly("Ancienne");
        assertThat(old.getId()).isNotNull();
    }

    @Test
    void publicFeedHonoursTheLimitAndCapsIt() {
        for (int i = 0; i < 5; i++) {
            service.create(req("News " + i, LocalDate.now().minusDays(i)));
        }
        assertThat(service.publicFeed(2)).hasSize(2);
        assertThat(service.publicFeed(9999)).hasSize(5); // capped at 12, only 5 exist
    }

    @Test
    void updateToggleAndDeleteWork() {
        NewsItem n = service.create(req("Titre", LocalDate.now()));

        NewsItem updated = service.update(n.getId(), new NewsItemRequest(
                "Titre v2", "Corps v2", null, LocalDate.now().plusDays(1), false));
        assertThat(updated.getTitle()).isEqualTo("Titre v2");
        assertThat(updated.getExternalLink()).isNull();
        assertThat(updated.isActive()).isFalse();

        service.delete(n.getId());
        assertThat(service.listAll()).extracting(NewsItem::getId).doesNotContain(n.getId());
    }

    @Test
    void rejectsNonHttpExternalLinks() {
        assertThatThrownBy(() -> service.create(new NewsItemRequest(
                "T", "B", "javascript:alert(1)", LocalDate.now(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
