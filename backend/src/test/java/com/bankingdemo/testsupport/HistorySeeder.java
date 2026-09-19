package com.bankingdemo.testsupport;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.account.SystemAccounts;
import com.bankingdemo.ledger.FinancialTransaction;
import com.bankingdemo.ledger.FinancialTransactionRepository;
import com.bankingdemo.ledger.LedgerDirection;
import com.bankingdemo.ledger.LedgerEntry;
import com.bankingdemo.ledger.LedgerEntryRepository;
import com.bankingdemo.ledger.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Writes historical ledger rows with explicit timestamps (the real money path
 * always stamps "now"), so tests can build months of history deterministically.
 * It writes the same rows the ledger engine would -- one transaction, one DEBIT
 * and one CREDIT entry -- but does not touch balances; tests that need a
 * balance set it themselves.
 */
public class HistorySeeder {

    private final FinancialTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final AccountRepository accounts;

    public HistorySeeder(FinancialTransactionRepository transactions, LedgerEntryRepository entries, AccountRepository accounts) {
        this.transactions = transactions;
        this.entries = entries;
        this.accounts = accounts;
    }

    public Account cash() {
        return accounts.findByAccountNumber(SystemAccounts.CASH_ACCOUNT_NUMBER).orElseThrow();
    }

    public Account billpay() {
        return accounts.findByAccountNumber(SystemAccounts.BILLPAY_ACCOUNT_NUMBER).orElseThrow();
    }

    /** A customer payment out of {@code from} into {@code to} (another customer, or a system account). Returns the transaction. */
    public FinancialTransaction payment(Long customerId, Account from, Account to, TransactionType type, BigDecimal amount,
                                        Instant at, String description, Long categoryId) {
        return write(customerId, from, to, type, amount, at, description, categoryId);
    }

    /** A withdrawal to system cash (counts as spending). */
    public FinancialTransaction withdrawal(Long customerId, Account from, BigDecimal amount, Instant at, String description, Long categoryId) {
        return write(customerId, from, cash(), TransactionType.WITHDRAWAL, amount, at, description, categoryId);
    }

    /** Simulated funding: a credit from system cash. Never counts as spending or income. */
    public FinancialTransaction deposit(Long customerId, Account to, BigDecimal amount, Instant at) {
        return write(customerId, cash(), to, TransactionType.DEPOSIT, amount, at, "Initial funding", null);
    }

    /** A transfer between two of the same customer's accounts (never spending). */
    public FinancialTransaction ownTransfer(Long customerId, Account from, Account to, BigDecimal amount, Instant at, String description) {
        return write(customerId, from, to, TransactionType.TRANSFER, amount, at, description, null);
    }

    /** A credit into {@code to} from another customer's account, described as a refund. */
    public FinancialTransaction refund(Long refundingCustomerId, Account from, Account to, BigDecimal amount, Instant at, String description) {
        return write(refundingCustomerId, from, to, TransactionType.TRANSFER, amount, at, description, null);
    }

    private FinancialTransaction write(Long initiatedBy, Account from, Account to, TransactionType type, BigDecimal amount,
                                       Instant at, String description, Long categoryId) {
        FinancialTransaction tx = new FinancialTransaction();
        tx.setReference("TXN-" + java.util.UUID.randomUUID());
        tx.setType(type);
        tx.setAmount(amount);
        tx.setInitiatedByCustomerId(initiatedBy);
        tx.setSourceAccountId(from.getId());
        tx.setDestinationAccountId(to.getId());
        tx.setDescription(description);
        tx.setCreatedAt(at);
        tx = transactions.saveAndFlush(tx);

        LedgerEntry debit = new LedgerEntry();
        debit.setFinancialTransactionId(tx.getId());
        debit.setAccountId(from.getId());
        debit.setDirection(LedgerDirection.DEBIT);
        debit.setAmount(amount);
        debit.setBalanceAfter(BigDecimal.ZERO);
        debit.setCategoryId(from.isSystem() ? null : categoryId);
        debit.setCreatedAt(at);
        entries.save(debit);

        LedgerEntry credit = new LedgerEntry();
        credit.setFinancialTransactionId(tx.getId());
        credit.setAccountId(to.getId());
        credit.setDirection(LedgerDirection.CREDIT);
        credit.setAmount(amount);
        credit.setBalanceAfter(BigDecimal.ZERO);
        credit.setCreatedAt(at);
        entries.save(credit);
        return tx;
    }
}
