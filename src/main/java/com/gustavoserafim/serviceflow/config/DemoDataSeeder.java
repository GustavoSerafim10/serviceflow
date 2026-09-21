package com.gustavoserafim.serviceflow.config;

import com.gustavoserafim.serviceflow.entity.Category;
import com.gustavoserafim.serviceflow.entity.Priority;
import com.gustavoserafim.serviceflow.entity.Role;
import com.gustavoserafim.serviceflow.entity.Ticket;
import com.gustavoserafim.serviceflow.entity.TicketStatus;
import com.gustavoserafim.serviceflow.entity.TicketSuggestion;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.repository.CategoryRepository;
import com.gustavoserafim.serviceflow.repository.TicketRepository;
import com.gustavoserafim.serviceflow.repository.TicketSuggestionRepository;
import com.gustavoserafim.serviceflow.repository.UserRepository;
import com.gustavoserafim.serviceflow.service.SlaRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Gera ~45 dias de chamados fictícios e realistas para DEMONSTRAÇÃO (dashboard, capturas de tela).
 *
 * Desligado por padrão: só roda com app.demo-data.enabled=true (variável DEMO_DATA=true) e
 * nunca em cima de dados existentes de demonstração (idempotente pelo usuário-marcador).
 * Escreve direto nos repositórios porque a API não permite "voltar no tempo" (createdAt é sempre agora).
 * A semente fixa do Random torna o resultado reproduzível. Não gera histórico nem comentários.
 *
 * NÃO ative em produção: cria usuários com senha conhecida (demo1234).
 */
@Component
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String MARKER_EMAIL = "ana.souza@demo.serviceflow.local";
    private static final String PASSWORD = "demo1234";
    private static final int DAYS = 45;

    /** Peso (proporção aproximada) de cada categoria padrão e títulos de exemplo. */
    private static final Map<String, Object[]> CATEGORIES = new java.util.LinkedHashMap<>();

    static {
        CATEGORIES.put("Software", new Object[]{25, new String[]{"Sistema lento ao salvar", "Erro ao abrir planilha", "Instalar programa"}});
        CATEGORIES.put("Rede", new Object[]{22, new String[]{"Sem internet no setor", "VPN não conecta", "Wi-Fi instável"}});
        CATEGORIES.put("Acesso e Senha", new Object[]{20, new String[]{"Conta bloqueada", "Esqueci minha senha", "Solicitar acesso"}});
        CATEGORIES.put("Hardware", new Object[]{15, new String[]{"Computador não liga", "Monitor sem imagem", "Teclado com defeito"}});
        CATEGORIES.put("E-mail", new Object[]{10, new String[]{"Outlook não abre", "Caixa de entrada cheia"}});
        CATEGORIES.put("Impressora", new Object[]{8, new String[]{"Impressora offline", "Troca de toner"}});
    }

    /** Técnicos com velocidades diferentes (multiplicador do tempo de resolução): torna a tabela interessante. */
    private record Tech(String name, String email, double speed) {
    }

    private static final List<Tech> TECHS = List.of(
            new Tech("Ana Souza", MARKER_EMAIL, 0.75),
            new Tech("Bruno Lima", "bruno.lima@demo.serviceflow.local", 1.0),
            new Tech("Carla Dias", "carla.dias@demo.serviceflow.local", 1.45));

    private static final List<String> REQUESTERS = List.of("Diego Rocha", "Elisa Prado", "Fábio Nunes", "Gabi Torres");

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TicketRepository ticketRepository;
    private final TicketSuggestionRepository suggestionRepository;
    private final SlaRuleService slaRuleService;
    private final PasswordEncoder passwordEncoder;
    private final BusinessHoursProperties calendar;
    private final Clock clock;

    public DemoDataSeeder(UserRepository userRepository, CategoryRepository categoryRepository,
                          TicketRepository ticketRepository, TicketSuggestionRepository suggestionRepository,
                          SlaRuleService slaRuleService, PasswordEncoder passwordEncoder,
                          BusinessHoursProperties calendar, Clock clock) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.ticketRepository = ticketRepository;
        this.suggestionRepository = suggestionRepository;
        this.slaRuleService = slaRuleService;
        this.passwordEncoder = passwordEncoder;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmailIgnoreCase(MARKER_EMAIL)) {
            log.info("Dados de demonstração já existem: nada a fazer");
            return;
        }

        Random random = new Random(42);
        String hash = passwordEncoder.encode(PASSWORD); // um hash só para todos os usuários fictícios (é só demo)

        List<User> technicians = new ArrayList<>();
        for (Tech tech : TECHS) {
            technicians.add(createUser(tech.name(), tech.email(), Role.TECNICO, hash));
        }
        List<User> requesters = new ArrayList<>();
        for (String name : REQUESTERS) {
            String email = name.toLowerCase().replace("á", "a").replace(" ", ".") + "@demo.serviceflow.local";
            requesters.add(createUser(name, email, Role.SOLICITANTE, hash));
        }

        List<Category> categories = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        Map<Long, String[]> titles = new java.util.HashMap<>();
        CATEGORIES.forEach((name, spec) -> categoryRepository.findByNameIgnoreCase(name).ifPresent(c -> {
            categories.add(c);
            weights.add((Integer) spec[0]);
            titles.put(c.getId(), (String[]) spec[1]);
        }));
        if (categories.isEmpty()) {
            log.warn("Categorias padrão não encontradas: dados de demonstração não gerados");
            return;
        }

        Instant now = clock.instant();
        ZoneId zone = calendar.zone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Ticket> tickets = new ArrayList<>();
        List<TicketSuggestion> suggestions = new ArrayList<>();

        for (int daysAgo = DAYS - 1; daysAgo >= 0; daysAgo--) {
            LocalDate date = today.minusDays(daysAgo);
            boolean weekend = date.getDayOfWeek().getValue() >= 6;
            // volume diário: fim de semana quase parado; dias úteis com leve tendência de crescimento
            int volume = weekend ? random.nextInt(2) : 3 + random.nextInt(4) + (DAYS - daysAgo) / 15;

            for (int i = 0; i < volume; i++) {
                Instant created = date.atTime(8 + random.nextInt(10), random.nextInt(60)).atZone(zone).toInstant();
                if (created.isAfter(now)) {
                    continue;
                }
                Category category = categories.get(pick(weights, random));
                Priority priority = Priority.values()[pick(List.of(8, 22, 45, 25), random)];
                Tech tech = TECHS.get(random.nextInt(TECHS.size()));
                User technician = technicians.get(TECHS.indexOf(tech));

                Ticket ticket = new Ticket();
                String[] options = titles.get(category.getId());
                ticket.setTitle(options[random.nextInt(options.length)]);
                ticket.setDescription("Chamado de demonstração gerado automaticamente.");
                ticket.setCategory(category);
                ticket.setRequester(requesters.get(random.nextInt(requesters.size())));
                ticket.setPriority(priority);
                ticket.setCreatedAt(created);
                ticket.setSlaDueAt(slaRuleService.dueAtFor(priority, created));

                double roll = random.nextDouble();
                long resolutionMinutes = Math.round(typicalMinutes(priority) * tech.speed() * Math.exp(random.nextGaussian() * 0.7));
                Instant resolvedAt = created.plus(resolutionMinutes, ChronoUnit.MINUTES);

                if (roll < 0.04) {
                    ticket.setStatus(TicketStatus.CANCELADO);
                } else if (!resolvedAt.isAfter(now)) {
                    ticket.setAssignee(technician);
                    ticket.setResolvedAt(resolvedAt);
                    ticket.setStatus(roll < 0.80 ? TicketStatus.FECHADO : TicketStatus.RESOLVIDO);
                } else if (random.nextDouble() < 0.6) {
                    ticket.setAssignee(technician);
                    ticket.setStatus(TicketStatus.EM_ATENDIMENTO);
                } else {
                    ticket.setStatus(TicketStatus.ABERTO);
                }
                tickets.add(ticket);

                // ~65% dos chamados foram abertos a partir de uma sugestão (aceita ou trocada), para o painel de feedback
                if (ticket.getStatus() != TicketStatus.CANCELADO && random.nextDouble() < 0.65) {
                    suggestions.add(suggestionFor(ticket, categories, random));
                }
            }
        }

        ticketRepository.saveAll(tickets);
        suggestionRepository.saveAll(suggestions);
        log.warn("DADOS DE DEMONSTRAÇÃO gerados: {} chamados, {} sugestões, {} técnicos (senha de todos: {}). "
                        + "Nunca use em produção.", tickets.size(), suggestions.size(), technicians.size(), PASSWORD);
    }

    private TicketSuggestion suggestionFor(Ticket ticket, List<Category> categories, Random random) {
        boolean categoryHit = random.nextDouble() < 0.72;
        boolean priorityHit = random.nextDouble() < 0.58;

        TicketSuggestion s = new TicketSuggestion();
        s.setUser(ticket.getRequester());
        s.setModelVersion("v1-demo");
        s.setCreatedAt(ticket.getCreatedAt().minusSeconds(60));
        s.setLinkedAt(ticket.getCreatedAt());
        s.setTicket(ticket);

        Category suggested = ticket.getCategory();
        while (!categoryHit && suggested.getId().equals(ticket.getCategory().getId())) {
            suggested = categories.get(random.nextInt(categories.size()));
        }
        s.setSuggestedCategory(suggested);
        s.setCategoryConfidence(round(0.45 + random.nextDouble() * 0.5));
        s.setCategoryAccepted(categoryHit);

        Priority suggestedPriority = ticket.getPriority();
        while (!priorityHit && suggestedPriority == ticket.getPriority()) {
            suggestedPriority = Priority.values()[random.nextInt(4)];
        }
        s.setSuggestedPriority(suggestedPriority);
        s.setPriorityConfidence(round(0.4 + random.nextDouble() * 0.5));
        s.setPriorityAccepted(priorityHit);
        return s;
    }

    private User createUser(String name, String email, Role role, String passwordHash) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        return userRepository.save(user);
    }

    /** Tempo "típico" de resolução (minutos corridos) por prioridade; a variação vem de uma log-normal. */
    private static double typicalMinutes(Priority priority) {
        return switch (priority) {
            case P1 -> 120;
            case P2 -> 300;
            case P3 -> 780;
            case P4 -> 1500;
        };
    }

    private static int pick(List<Integer> weights, Random random) {
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                return i;
            }
        }
        return weights.size() - 1;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
