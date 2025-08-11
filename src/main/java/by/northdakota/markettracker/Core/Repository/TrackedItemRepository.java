package by.northdakota.markettracker.Core.Repository;

import by.northdakota.markettracker.Core.Entity.Marketplace;
import by.northdakota.markettracker.Core.Entity.TrackedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.yaml.snakeyaml.error.Mark;

import java.util.List;
import java.util.Optional;

public interface TrackedItemRepository extends JpaRepository<TrackedItem, Long> {

    void deleteByArticleAndChatIdAndMarketplace(String article, Long chatId,Marketplace marketplace);

    Optional<List<TrackedItem>> findAllByMarketplace(Marketplace marketplace);

    Optional<List<TrackedItem>> findAllByChatIdAndMarketplace(Long chatId, Marketplace marketplace);

    boolean existsByArticleAndChatIdAndMarketplace(String article, Long chatId, Marketplace marketplace);

}
