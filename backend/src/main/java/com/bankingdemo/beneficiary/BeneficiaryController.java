package com.bankingdemo.beneficiary;

import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.beneficiary.dto.AddBeneficiaryRequest;
import com.bankingdemo.beneficiary.dto.BeneficiaryResponse;
import com.bankingdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;
    private final AccountRepository accountRepository;

    @GetMapping
    public List<BeneficiaryResponse> list() {
        Long customerId = SecurityUtils.requireCustomerId();
        return beneficiaryService.list(customerId).stream()
                .map(b -> BeneficiaryResponse.from(b, accountRepository.findById(b.getBeneficiaryAccountId()).orElseThrow()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<BeneficiaryResponse> add(@Valid @RequestBody AddBeneficiaryRequest request) {
        Long customerId = SecurityUtils.requireCustomerId();
        Beneficiary beneficiary = beneficiaryService.add(customerId, request.accountNumber(), request.nickname());
        var account = accountRepository.findById(beneficiary.getBeneficiaryAccountId()).orElseThrow();
        return ResponseEntity.ok(BeneficiaryResponse.from(beneficiary, account));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        Long customerId = SecurityUtils.requireCustomerId();
        beneficiaryService.remove(customerId, id);
        return ResponseEntity.noContent().build();
    }
}
