package rea.ohyeahworld.newpgapp.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rea.ohyeahworld.newpgapp.repository.PaymentRepository;
import rea.ohyeahworld.newpgapp.service.DataValidationException;
import rea.ohyeahworld.newpgapp.service.PaymentService;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/payments")
public class PaymentsController {
    private final PaymentRepository repository;
    private final PaymentService service;

    public PaymentsController(PaymentRepository repository, PaymentService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("items", repository.findAll());
        return "payments/list";
    }

    @PostMapping("/create")
    public String create(@RequestParam Integer apartmentNumber,
                         @RequestParam LocalDate paymentDate,
                         @RequestParam BigDecimal amount,
                         @RequestParam(required = false) String description,
                         RedirectAttributes redirect) {
        try {
            service.create(apartmentNumber, paymentDate, amount, description);
            redirect.addFlashAttribute("message", "Платеж добавлен.");
        } catch (DataValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", rootMessage(e,
                    "Не удалось добавить платеж."));
        }
        return "redirect:/payments";
    }

    @PostMapping("/update")
    public String update(@RequestParam Long id,
                         @RequestParam Integer apartmentNumber,
                         @RequestParam LocalDate paymentDate,
                         @RequestParam BigDecimal amount,
                         @RequestParam(required = false) String description,
                         RedirectAttributes redirect) {
        try {
            service.update(id, apartmentNumber, paymentDate, amount, description);
            redirect.addFlashAttribute("message", "Платеж изменен.");
        } catch (DataValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", rootMessage(e,
                    "Не удалось изменить платеж."));
        }
        return "redirect:/payments";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, RedirectAttributes redirect) {
        try {
            service.delete(id);
            redirect.addFlashAttribute("message", "Платеж удален.");
        } catch (DataValidationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", rootMessage(e,
                    "Не удалось удалить платеж."));
        }
        return "redirect:/payments";
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
