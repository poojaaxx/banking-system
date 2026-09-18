package com.bankingdemo.beneficiary;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<Beneficiary> list(Long customerId) {
        return beneficiaryRepository.findByCustomerIdOrderByNicknameAsc(customerId);
    }

    @Transactional
    public Beneficiary add(Long customerId, String accountNumber, String nickname) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .filter(a -> !a.isSystem())
                .orElseThrow(() -> ApiException.badRequest("No account found with that account number"));
        if (beneficiaryRepository.existsByCustomerIdAndBeneficiaryAccountId(customerId, account.getId())) {
            throw ApiException.conflict("This account is already saved as a beneficiary");
        }
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setCustomerId(customerId);
        beneficiary.setBeneficiaryAccountId(account.getId());
        beneficiary.setNickname(nickname);
        return beneficiaryRepository.save(beneficiary);
    }

    @Transactional
    public void remove(Long customerId, Long beneficiaryId) {
        Beneficiary beneficiary = beneficiaryRepository.findByCustomerIdAndId(customerId, beneficiaryId)
                .orElseThrow(() -> ApiException.notFound("Beneficiary not found"));
        beneficiaryRepository.delete(beneficiary);
    }
}
