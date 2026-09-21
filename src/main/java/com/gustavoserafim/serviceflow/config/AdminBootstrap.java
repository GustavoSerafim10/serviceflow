package com.gustavoserafim.serviceflow.config;

import com.gustavoserafim.serviceflow.entity.Role;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Resolve o "ovo e a galinha": só ADMIN cria usuários, mas no primeiro boot
 * não existe nenhum. O ApplicationRunner roda uma vez logo após a aplicação
 * subir; se não houver nenhum ADMIN, cria o primeiro com os dados de
 * app.bootstrap-admin.* (application.yml ou variáveis de ambiente).
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.bootstrap-admin.name}") String name,
                          @Value("${app.bootstrap-admin.email}") String email,
                          @Value("${app.bootstrap-admin.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        // A condição é "não existe nenhum ADMIN" (e não "não existe nenhum usuário"): se só houver
        // usuários comuns (ex: dados de demonstração), ainda falta quem administre o sistema.
        if (userRepository.existsByRole(Role.ADMIN) || userRepository.existsByEmailIgnoreCase(email.trim())) {
            return;
        }

        User admin = new User();
        admin.setName(name);
        admin.setEmail(email.trim().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        log.warn("Nenhum ADMIN encontrado: ADMIN inicial criado ({}). Troque a senha padrão!", admin.getEmail());
    }
}
