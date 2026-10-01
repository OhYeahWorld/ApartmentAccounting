package rea.ohyeahworld.newpgapp.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rea.ohyeahworld.newpgapp.repository.ChargeRepository;
import rea.ohyeahworld.newpgapp.service.ChargeService;
import rea.ohyeahworld.newpgapp.service.DataValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/charges")
public class ChargesController {
    private final ChargeRepository repository;
    private final ChargeService service;

    public ChargesController(ChargeRepository repository, ChargeService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("items", repository.findAll());
        return "charges/list";
    }

    @PostMapping("/create")
    public String create(@RequestParam Integer apartmentNumber,
                         @RequestParam LocalDate period,
                         @RequestParam BigDecimal amount,
                         @RequestParam(required = false) String description,
                         RedirectAttributes redirect) {
        try {
            service.create(apartmentNumber, period, amount, description);
            redirect.addFlashAttribute("message", "Начисление добавлено.");
        } catch (DataValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", rootMessage(e,
                    "Не удалось добавить начисление. Возможно, такая квартира за этот месяц уже есть."));
        }
        return "redirect:/charges";
    }

    @PostMapping("/update")
    public String update(@RequestParam Long id,
                         @RequestParam Integer apartmentNumber,
                         @RequestParam LocalDate period,
                         @RequestParam BigDecimal amount,
                         @RequestParam(required = false) String description,
                         RedirectAttributes redirect) {
        try {
            service.update(id, apartmentNumber, period, amount, description);
            redirect.addFlashAttribute("message", "Начисление изменено.");
        } catch (DataValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", rootMessage(e,
                    "Не удалось изменить начисление."));
        }
        return "redirect:/charges";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, RedirectAttributes redirect) {
        try {
            service.delete(id);
            redirect.addFlashAttribute("message", "Начисление удалено.");
        } catch (DataValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", rootMessage(e,
                    "Не удалось удалить начисление."));
        }
        return "redirect:/charges";
    }

    private static String rootMessage(DataIntegrityViolationException e, String fallback) {
        Throwable t = e.getMostSpecificCause();
        String message = t != null ? t.getMessage() : null;
        if (message == null || message.isBlank()) {
            return fallback;
        }
        int idx = message.indexOf('\n');
        if (idx >= 0 && idx + 1 < message.length()) {
            message = message.substring(idx + 1);
        }
        return message.trim();
    }
}
