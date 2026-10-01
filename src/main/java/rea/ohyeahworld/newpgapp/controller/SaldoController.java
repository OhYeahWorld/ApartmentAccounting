package rea.ohyeahworld.newpgapp.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rea.ohyeahworld.newpgapp.repository.SaldoRepository;
import rea.ohyeahworld.newpgapp.service.DataValidationException;
import rea.ohyeahworld.newpgapp.service.SaldoService;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/saldo")
public class SaldoController {
    private final SaldoRepository repository;
    private final SaldoService service;

    public SaldoController(SaldoRepository repository, SaldoService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("items", repository.findAll());
        return "saldo/list";
    }

    @PostMapping("/create")
    public String create(@RequestParam Integer apartmentNumber,
                         @RequestParam LocalDate period,
                         @RequestParam(required = false) BigDecimal openingBalance,
                         @RequestParam(required = false) BigDecimal closingBalance,
                         RedirectAttributes redirect) {
        try {
            service.create(apartmentNumber, period, openingBalance, closingBalance);
            redirect.addFlashAttribute("message",
                    "Сальдо добавлено (входящее и исходящее рассчитаны автоматически).");
        } catch (DataValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", rootMessage(e,
                    "Не удалось добавить запись: операция нарушает целостность данных."));
        }
        return "redirect:/saldo";
    }

    @PostMapping("/update")
    public String update(@RequestParam Long id,
                         @RequestParam Integer apartmentNumber,
                         @RequestParam LocalDate period,
                         @RequestParam BigDecimal openingBalance,
                         @RequestParam BigDecimal closingBalance,
                         RedirectAttributes redirect) {
        try {
            service.update(id, apartmentNumber, period, openingBalance, closingBalance);
            redirect.addFlashAttribute("message", "Сальдо изменено.");
        } catch (DataValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", rootMessage(e,
                    "Не удалось изменить запись: операция нарушает целостность данных."));
        }
        return "redirect:/saldo";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, RedirectAttributes redirect) {
        try {
            service.delete(id);
            redirect.addFlashAttribute("message", "Сальдо удалено.");
        } catch (DataValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", rootMessage(e,
                    "Не удалось удалить запись: операция нарушает целостность данных."));
        }
        return "redirect:/saldo";
    }

    private static String rootMessage(DataIntegrityViolationException e, String fallback) {
        Throwable t = e.getMostSpecificCause();
        String message = t != null ? t.getMessage() : null;
        if (message == null || message.isBlank()) {
            return fallback;
        }
        // сообщения триггеров приходят с префиксом ERROR: — обрезаем служебное
        int idx = message.indexOf('\n');
        if (idx >= 0 && idx + 1 < message.length()) {
            message = message.substring(idx + 1);
        }
        return message.trim();
    }
}
