package com.gustavoserafim.serviceflow.repository;

import com.gustavoserafim.serviceflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Optional<T>: "pode ou não haver resultado". Força quem chama a tratar
    // o caso "não achei", em vez de receber null e estourar NullPointerException.
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
