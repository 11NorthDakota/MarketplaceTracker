package by.northdakota.markettracker.Core.Repository;

import by.northdakota.markettracker.Core.Entity.TrackedItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackedItemRepository extends JpaRepository<TrackedItem, Long> {

    void deleteByArticleAndChatId(String article, Long chatId);

    List<TrackedItem> findAllByChatId(Long chatId);

    boolean existsByArticleAndChatId(String article, Long chatId);

}
