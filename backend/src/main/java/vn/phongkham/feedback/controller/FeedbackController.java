package vn.phongkham.feedback.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.phongkham.auth.security.AuthUser;
import vn.phongkham.common.Patterns;
import vn.phongkham.feedback.model.Feedback;
import vn.phongkham.feedback.service.FeedbackService;
import vn.phongkham.feedback.service.FeedbackService.FeedbackRequest;
import vn.phongkham.feedback.service.FeedbackService.FeedbackResponse;
import vn.phongkham.feedback.service.FeedbackService.StatusRequest;

@Tag(name = "Phản hồi")
@RestController
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('PATIENT')")
    @PostMapping("/me/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody FeedbackRequest req) {
        return service.create(me, req);
    }

    @PreAuthorize("hasRole('PATIENT')")
    @GetMapping("/me/feedback")
    public List<FeedbackResponse> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @PreAuthorize(Patterns.ADMIN)
    @GetMapping("/feedback")
    public List<FeedbackResponse> list(@RequestParam(required = false) Feedback.Status status) {
        return service.list(status);
    }

    @PreAuthorize(Patterns.ADMIN)
    @PatchMapping("/feedback/{id}")
    public FeedbackResponse setStatus(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                      @Valid @RequestBody StatusRequest req) {
        return service.setStatus(me, id, req.status());
    }
}
