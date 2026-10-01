package rea.ohyeahworld.newpgapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rea.ohyeahworld.newpgapp.model.Charge;
import rea.ohyeahworld.newpgapp.repository.ChargeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/charges")
public class ChargesController {
    private final ChargeRepository repository;

    public ChargesController(ChargeRepository repository) {
        this.repository = repository;
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
            repository.insert(new Charge(null, apartmentNumber, period, amount, description));
            redirect.addFlashAttribute("message", "Начисление добавлено.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Не удалось добавить начисление. Возможно, такая квартира за этот месяц уже есть.");
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
            repository.update(new Charge(id, apartmentNumber, period, amount, description));
            redirect.addFlashAttribute("message", "Начисление изменено.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Не удалось изменить начисление.");
        }
        return "redirect:/charges";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, RedirectAttributes redirect) {
        repository.delete(id);
        redirect.addFlashAttribute("message", "Начисление удалено.");
        return "redirect:/charges";
    }
}
