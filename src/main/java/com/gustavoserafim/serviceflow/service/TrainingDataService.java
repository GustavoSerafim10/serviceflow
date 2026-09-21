package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exporta os chamados reais no MESMO formato do dataset do serviço Python
 * (title,description,category,priority), pronto para o `python -m app.train`.
 *
 * Sobre a qualidade dos rótulos: a categoria/prioridade gravadas são o que o
 * usuário escolheu (com a sugestão à vista, se aceitou) e o que a equipe deixou.
 * É a melhor fonte disponível, mas não é "verdade absoluta": revise antes de
 * treinar. O CSV é montado em memória, adequado ao porte deste projeto; para
 * milhões de chamados, o correto seria transmitir (streaming) em partes.
 */
@Service
public class TrainingDataService {

    private static final String HEADER = "title,description,category,priority\n";

    private final TicketRepository ticketRepository;

    public TrainingDataService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public String exportCsv() {
        StringBuilder csv = new StringBuilder(HEADER);
        for (TicketRepository.TrainingRow row : ticketRepository.findTrainingRows()) {
            csv.append(Csv.cell(row.getTitle())).append(',')
                    .append(Csv.cell(row.getDescription())).append(',')
                    .append(Csv.cell(row.getCategory())).append(',')
                    .append(row.getPriority().name()).append('\n');
        }
        return csv.toString();
    }
}
