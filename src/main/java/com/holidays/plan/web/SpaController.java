package com.holidays.plan.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Rimanda le rotte del frontend (React Router) a index.html. */
@Controller
public class SpaController {
    @GetMapping({"/", "/calendario", "/richieste", "/approvazioni", "/admin", "/admin/**", "/login", "/profilo"})
    public String index() {
        return "forward:/index.html";
    }
}
