package com.bankingdemo.savingsgoal;

import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.ledger.MoneyMovementReceipt;
import com.bankingdemo.savingsgoal.dto.ContributeRequest;
import com.bankingdemo.savingsgoal.dto.CreateGoalRequest;
import com.bankingdemo.savingsgoal.dto.SavingsGoalResponse;
import com.bankingdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/savings-goals")
@RequiredArgsConstructor
public class SavingsGoalController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 80;

    private final SavingsGoalService savingsGoalService;
    private final AccountRepository accountRepository;

    @GetMapping
    public List<SavingsGoalResponse> list() {
        Long customerId = SecurityUtils.requireCustomerId();
        return savingsGoalService.list(customerId).stream()
                .map(g -> SavingsGoalResponse.from(g, accountRepository.findById(g.getLinkedAccountId()).orElseThrow()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<SavingsGoalResponse> create(@Valid @RequestBody CreateGoalRequest request) {
        Long customerId = SecurityUtils.requireCustomerId();
        SavingsGoal goal = savingsGoalService.create(customerId, request.name(), request.targetAmount(), request.targetDate());
        return ResponseEntity.ok(SavingsGoalResponse.from(goal, accountRepository.findById(goal.getLinkedAccountId()).orElseThrow()));
    }

    @PostMapping("/{id}/contribute")
    public MoneyMovementReceipt contribute(@PathVariable Long id, @Valid @RequestBody ContributeRequest request,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw ApiException.badRequest("A valid Idempotency-Key header is required");
        }
        Long customerId = SecurityUtils.requireCustomerId();
        return savingsGoalService.contribute(customerId, id, request.sourceAccountId(), request.amount(), idempotencyKey);
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(@PathVariable Long id) {
        Long customerId = SecurityUtils.requireCustomerId();
        savingsGoalService.close(customerId, id);
        return ResponseEntity.noContent().build();
    }
}
