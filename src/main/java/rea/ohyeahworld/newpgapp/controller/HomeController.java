package rea.ohyeahworld.newpgapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import rea.ohyeahworld.newpgapp.repository.ChargeRepository;
import rea.ohyeahworld.newpgapp.repository.PaymentRepository;
import rea.ohyeahworld.newpgapp.repository.SaldoRepository;
import rea.ohyeahworld.newpgapp.service.ReportService;

import java.time.LocalDate;

@Controller
public class HomeController {
    private final SaldoRepository saldoRepository;
    private final ChargeRepository chargeRepository;
    private final PaymentRepository paymentRepository;
    private final ReportService reportService;

    public HomeController(SaldoRepository saldoRepository,
                           ChargeRepository chargeRepository,
                           PaymentRepository paymentRepository,
                           ReportService reportService) {
        this.saldoRepository = saldoRepository;
        this.chargeRepository = chargeRepository;
        this.paymentRepository = paymentRepository;
        this.reportService = reportService;
    }

    @GetMapping("/")
    public String home(Model model) {
        // отчетная дата — начало текущего месяца (данные за прошлые годы
        // учитываются через входящее сальдо / историю начислений и платежей)
        LocalDate asOf = LocalDate.now().withDayOfMonth(1);
        model.addAttribute("saldoCount", saldoRepository.count());
        model.addAttribute("chargesCount", chargeRepository.count());
        model.addAttribute("paymentsCount", paymentRepository.count());
        model.addAttribute("totalDebt", reportService.totalDebt(asOf));
        model.addAttribute("asOf", asOf);
        return "index";
    }
}
