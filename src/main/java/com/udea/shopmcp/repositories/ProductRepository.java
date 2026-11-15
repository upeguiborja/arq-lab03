package com.udea.shopmcp.repositories;

import com.udea.shopmcp.models.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByQuantityGreaterThanOrderByName(int minimum);

    List<Product> findByNameContainingIgnoreCaseOrderByName(String fragment);
    @Query("select p.id from Product p where lower(p.name) = lower(:name)")
    Optional<Long> findIdByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> lockForUpdate(Collection<Long> ids);
}
