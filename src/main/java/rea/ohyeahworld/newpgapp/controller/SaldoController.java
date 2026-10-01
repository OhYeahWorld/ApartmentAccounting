package rea.ohyeahworld.newpgapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rea.ohyeahworld.newpgapp.model.Saldo;
import rea.ohyeahworld.newpgapp.repository.SaldoRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/saldo")
public class SaldoController {
    private final SaldoRepository repository;

    public SaldoController(SaldoRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("items", repository.findAll());
        return "saldo/list";
    }

    @PostMapping("/create")
    public String create(@RequestParam Integer apartmentNumber,
                         @RequestParam LocalDate period,
                         @RequestParam BigDecimal openingBalance,
                         @RequestParam BigDecimal closingBalance,
                         RedirectAttributes redirect) {
        try {
            repository.insert(new Saldo(null, apartmentNumber, period, openingBalance, closingBalance));
            redirect.addFlashAttribute("message", "Сальдо добавлено.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Не удалось добавить запись. Возможно, такая квартира за этот период уже есть.");
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
            repository.update(new Saldo(id, apartmentNumber, period, openingBalance, closingBalance));
            redirect.addFlashAttribute("message", "Сальдо изменено.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Не удалось изменить запись.");
        }
        return "redirect:/saldo";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, RedirectAttributes redirect) {
        repository.delete(id);
        redirect.addFlashAttribute("message", "Сальдо удалено.");
        return "redirect:/saldo";
    }
}
