package rea.ohyeahworld.newpgapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rea.ohyeahworld.newpgapp.service.ReportService;

import java.time.LocalDate;

@Controller
@RequestMapping("/reports")
public class ReportsController {
    private final ReportService reportService;

    public ReportsController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/turnover")
    public String turnover(@RequestParam(defaultValue = "2017") int year, Model model) {
        model.addAttribute("year", year);
        model.addAttribute("items", reportService.turnover(year));
        return "reports/turnover";
    }

    @GetMapping("/apartment")
    public String apartment(@RequestParam(defaultValue = "1") int apartment,
                            @RequestParam(defaultValue = "2017") int year,
                            Model model) {
        model.addAttribute("report", reportService.apartmentStatement(apartment, year));
        return "reports/apartment";
    }

    @GetMapping("/debtors")
    public String debtors(@RequestParam(defaultValue = "2017-10-01") LocalDate asOf,
                          Model model) {
        model.addAttribute("asOf", asOf);
        model.addAttribute("items", reportService.debtors(asOf));
        return "reports/debtors";
    }
}
