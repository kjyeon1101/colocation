package com.sds.colocation.customer;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sds.colocation.billing.FeeCalculator;
import com.sds.colocation.common.BusinessException;
import com.sds.colocation.allocation.Allocation;
import com.sds.colocation.allocation.AllocationRepository;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AllocationRepository allocationRepository;
    private final FeeCalculator feeCalculator;
    private final Clock clock;

    public CustomerService(CustomerRepository customerRepository, AllocationRepository allocationRepository,
                           FeeCalculator feeCalculator, Clock clock) {
        this.customerRepository = customerRepository;
        this.allocationRepository = allocationRepository;
        this.feeCalculator = feeCalculator;
        this.clock = clock;
    }

    public List<CustomerView> findAll() {
        Map<Long, List<Allocation>> allocationsByCustomer = allocationRepository.findAllActiveWithDetails().stream()
                .collect(Collectors.groupingBy(allocation -> allocation.getCustomer().getId()));
        return customerRepository.findAllByOrderByIdAsc().stream()
                .map(customer -> toView(customer, allocationsByCustomer.getOrDefault(customer.getId(), List.of())))
                .toList();
    }

    @Transactional
    public CustomerView create(CustomerCreateRequest request) {
        Customer customer = customerRepository.save(
                new Customer(request.name(), request.tier(), request.contactName(), request.contactEmail()));
        return toView(customer, List.of());
    }

    public Customer getCustomer(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> BusinessException.notFound("고객", id));
    }

    private CustomerView toView(Customer customer, List<Allocation> allocations) {
        LocalDate today = LocalDate.now(clock);
        return new CustomerView(
                customer.getId(),
                customer.getName(),
                customer.getTier(),
                customer.getContactName(),
                customer.getContactEmail(),
                allocations.size(),
                allocations.stream().mapToInt(allocation -> allocation.getServers().size()).sum(),
                allocations.stream().mapToInt(Allocation::getSizeUnits).sum(),
                allocations.stream().mapToInt(Allocation::getTotalPowerWatts).sum(),
                allocations.stream().mapToLong(allocation -> feeCalculator.currentFee(allocation, today)).sum()
        );
    }
}
