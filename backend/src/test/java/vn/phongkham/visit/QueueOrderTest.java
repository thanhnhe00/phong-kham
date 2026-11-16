package vn.phongkham.visit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.phongkham.visit.model.VisitEnums.QueueGroup;
import vn.phongkham.visit.service.QueueOrder;
import vn.phongkham.visit.service.QueueOrder.Entry;

/** Thứ tự gọi khám: đúng giờ trước, muộn sau, cứ N lượt đặt lịch xen 1 vãng lai */
class QueueOrderTest {

    private static final Instant T = Instant.parse("2026-11-16T01:00:00Z");

    private static Entry e(long id, QueueGroup g, int minute) {
        return new Entry(id, g, T.plusSeconds(minute * 60L));
    }

    private final List<Entry> waiting = List.of(
            e(1, QueueGroup.ON_TIME, 5), e(2, QueueGroup.ON_TIME, 10), e(3, QueueGroup.LATE, 0),
            e(4, QueueGroup.WALK_IN, 1), e(5, QueueGroup.WALK_IN, 2));

    @Test
    void onTimeBeforeLateThenWalkInAfterEveryThreeBooked() {
        assertThat(QueueOrder.order(waiting, 0, 3)).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void walkInIsServedSoonerWhenBookedStreakIsAlreadyLong() {
        assertThat(QueueOrder.order(waiting, 2, 3)).containsExactly(1L, 4L, 2L, 3L, 5L);
    }

    @Test
    void zeroMeansWalkInsGoLast() {
        assertThat(QueueOrder.order(waiting, 10, 0)).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void onlyWalkInsStillGetCalled() {
        assertThat(QueueOrder.order(List.of(e(4, QueueGroup.WALK_IN, 1)), 0, 3)).containsExactly(4L);
    }

    @Test
    void streakCountsBookedCallsSinceLastWalkIn() {
        assertThat(QueueOrder.bookedStreak(List.of(QueueGroup.ON_TIME, QueueGroup.LATE, QueueGroup.WALK_IN, QueueGroup.ON_TIME)))
                .isEqualTo(2);
        assertThat(QueueOrder.bookedStreak(List.of())).isZero();
    }
}
