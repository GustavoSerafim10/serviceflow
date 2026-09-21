package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.UserCreateRequest;
import com.gustavoserafim.serviceflow.dto.UserResponse;
import com.gustavoserafim.serviceflow.dto.UserUpdateRequest;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.exception.ConflictException;
import com.gustavoserafim.serviceflow.exception.ResourceNotFoundException;
import com.gustavoserafim.serviceflow.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Regras de negócio de usuários. Novidade em relação ao CategoryService:
 * recebe um PasswordEncoder (bean definido no SecurityConfig) para gerar o
 * hash BCrypt da senha antes de gravar.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(getOrThrow(id));
    }

    // Usado por GET /api/users/me: o "email" vem do token já validado.
    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado"));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        // Normaliza: "  Ana@Empresa.com " e "ana@empresa.com" são o mesmo login.
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Já existe um usuário com o e-mail '" + email + "'");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        // BCrypt embute um "salt" aleatório em cada hash: a mesma senha gera
        // hashes diferentes, o que derrota tabelas de hashes pré-calculadas.
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = getOrThrow(id);
        user.setName(request.name().trim());
        user.setRole(request.role());
        return UserResponse.from(user); // dirty checking faz o UPDATE
    }

    @Transactional
    public void deactivate(Long id) {
        getOrThrow(id).setActive(false);
    }

    private User getOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", id));
    }
}
