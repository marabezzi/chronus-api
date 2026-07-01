package br.com.atom.chronus_api.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.atom.chronus_api.dtos.AfdResponseDTO;
import br.com.atom.chronus_api.service.BatidasLogService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/batidas")
@RequiredArgsConstructor
public class BatidasLogController {

    private final BatidasLogService batidasLogService;

    @GetMapping
    public ResponseEntity<AfdResponseDTO> buscarPagina(
            @RequestParam(value = "nsr", required = false) Long nsr) {

        AfdResponseDTO resultado = batidasLogService.buscarBatidas(nsr);

        return resultado != null
                ? ResponseEntity.ok(resultado)
                : ResponseEntity.status(503).build();
    }


    @GetMapping("data")
    public ResponseEntity<AfdResponseDTO> buscaData(
        @RequestParam(value = "dataInicial")
        @DateTimeFormat(pattern = "ddMMyyyy") LocalDate dataInicial,
        @RequestParam(value = "dataFinal", required = false)
        @DateTimeFormat(pattern = "ddMMyyyy") LocalDate dataFinal) {
    
            AfdResponseDTO resultado = batidasLogService.buscarData(dataInicial, dataFinal);
    
            return resultado != null
                ? ResponseEntity.ok(resultado)
                : ResponseEntity.status(503).build();
    }
}