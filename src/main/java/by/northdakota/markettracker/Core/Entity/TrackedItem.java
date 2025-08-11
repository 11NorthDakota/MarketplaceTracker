package by.northdakota.markettracker.Core.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;


@Entity
@Table(name="tracked_items")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TrackedItem {

    @Id
    @SequenceGenerator(
            name="tracked_item_seq",sequenceName = "tracked_item_sequence",
            initialValue = 1,allocationSize = 20)
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "tracked_item_seq")
    private Long id;
    @Column(nullable = false)
    private String article;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private BigDecimal currentPrice;
    @Column(nullable = false)
    private BigDecimal basicPrice;
    @Column
    private BigDecimal salePrice;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Marketplace marketplace;
    @Column(nullable = false)
    private Long chatId;
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL,orphanRemoval = true)
    private List<PriceHistory> priceHistory;

    @Override
    public String toString() {
        return "TrackedItem{" +
                "chatId=" + chatId +
                ", basicPrice=" + basicPrice +
                ", currentPrice=" + currentPrice +
                ", title='" + title + '\'' +
                ", article='" + article + '\'' +
                ", id=" + id +
                '}';
    }
}
