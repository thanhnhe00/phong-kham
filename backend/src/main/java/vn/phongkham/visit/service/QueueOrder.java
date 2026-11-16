package vn.phongkham.visit.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import vn.phongkham.visit.model.VisitEnums.QueueGroup;

/**
 * Thứ tự gọi khám (hàm thuần, không đụng DB): người đặt lịch đúng giờ trước, đến muộn sau, cùng nhóm theo giờ check-in;
 * cứ sau walkInEvery lượt người đặt lịch thì xen một người vãng lai để vãng lai không phải chờ mãi (0 = vãng lai sau cùng).
 * Lễ tân không sửa được thứ tự này.
 */
public final class QueueOrder {

    public record Entry(Long id, QueueGroup group, Instant checkedInAt) {
    }

    private QueueOrder() {
    }

    /**
     * @param waiting      người đang chờ
     * @param bookedStreak số lượt người đặt lịch được gọi liên tiếp gần nhất (tính từ lần gọi vãng lai gần nhất)
     */
    public static List<Long> order(List<Entry> waiting, int bookedStreak, int walkInEvery) {
        Comparator<Entry> byGroupThenTime = Comparator.comparing((Entry e) -> e.group() == QueueGroup.LATE ? 1 : 0)
                .thenComparing(Entry::checkedInAt).thenComparing(Entry::id);
        LinkedList<Entry> booked = new LinkedList<>(waiting.stream().filter(e -> e.group() != QueueGroup.WALK_IN)
                .sorted(byGroupThenTime).toList());
        LinkedList<Entry> walkIns = new LinkedList<>(waiting.stream().filter(e -> e.group() == QueueGroup.WALK_IN)
                .sorted(Comparator.comparing(Entry::checkedInAt).thenComparing(Entry::id)).toList());
        List<Long> result = new ArrayList<>(waiting.size());
        int streak = bookedStreak;
        while (!booked.isEmpty() || !walkIns.isEmpty()) {
            boolean walkInTurn = !walkIns.isEmpty() && (booked.isEmpty() || (walkInEvery > 0 && streak >= walkInEvery));
            if (walkInTurn) {
                result.add(walkIns.removeFirst().id());
                streak = 0;
            } else {
                result.add(booked.removeFirst().id());
                streak++;
            }
        }
        return result;
    }

    /** Đếm lượt người đặt lịch được gọi liên tiếp gần nhất; groupsByCallTimeDesc: nhóm của các lượt đã gọi, mới nhất trước */
    public static int bookedStreak(List<QueueGroup> groupsByCallTimeDesc) {
        int n = 0;
        for (QueueGroup g : groupsByCallTimeDesc) {
            if (g == QueueGroup.WALK_IN) {
                break;
            }
            n++;
        }
        return n;
    }
}
