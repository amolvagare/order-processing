package com.ecommerce.orderprocessing.repository;

import com.ecommerce.orderprocessing.model.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for CustomerAddress entity operations.
 */
@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

	List<CustomerAddress> findByCustomerIdAndDeletedFalse(Long customerId);

	Optional<CustomerAddress> findByIdAndCustomerId(Long addressId, Long customerId);

	Optional<CustomerAddress> findByIdAndCustomerIdAndDeletedFalse(Long addressId, Long customerId);
}
