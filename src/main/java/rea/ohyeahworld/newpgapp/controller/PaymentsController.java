package rea.ohyeahworld.newpgapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rea.ohyeahworld.newpgapp.model.Payment;
import rea.ohyeahworld.newpgapp.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/payments")
public class PaymentsController {
    private final PaymentRepository repository;

    public PaymentsController(PaymentRepository repository) {
        this.repository = repository;
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
            repository.insert(new Payment(null, apartmentNumber, paymentDate, amount, description));
            redirect.addFlashAttribute("message", "Платеж добавлен.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Не удалось добавить платеж.");
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
            repository.update(new Payment(id, apartmentNumber, paymentDate, amount, description));
            redirect.addFlashAttribute("message", "Платеж изменен.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Не удалось изменить платеж.");
        }
        return "redirect:/payments";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, RedirectAttributes redirect) {
        repository.delete(id);
        redirect.addFlashAttribute("message", "Платеж удален.");
        return "redirect:/payments";
    }
}
