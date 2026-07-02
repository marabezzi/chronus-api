package br.com.atom.chronus_api.controller;

import br.com.atom.chronus_api.dtos.FuncionarioResponseDTO;
import br.com.atom.chronus_api.service.FuncionariosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/api/funcionarios")
@RequiredArgsConstructor
public class FuncionariosController {
 
    private final FuncionariosService funcionariosService;
 
    /**
     * GET /api/funcionarios          → lista todos da API
     * GET /api/funcionarios?pis=xxx  → filtra pelo PIS (11 ou 12 dígitos)
     */
    @GetMapping
    public ResponseEntity<?> buscar(@RequestParam(required = false) String pis) {
 
        List<FuncionarioResponseDTO> lista = funcionariosService.listarFuncionarios();
 
        if (lista.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
 
        // PIS informado → filtra (normaliza para 12 dígitos antes de comparar)
        if (pis != null && !pis.isBlank()) {
            String pisNormalizado = pis.length() == 11 ? "0" + pis : pis;
            log.info("Filtrando por PIS: {} (normalizado: {})", pis, pisNormalizado);
 
            return lista.stream()
                    .filter(f -> pisNormalizado.equals(f.getPis()))
                    .findFirst()
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> {
                        log.warn("Funcionário não encontrado para PIS: {}", pis);
                        return ResponseEntity.notFound().build();
                    });
        }
 
        // Sem PIS → retorna todos
        log.info("Listando todos: {} funcionário(s)", lista.size());
        return ResponseEntity.ok(lista);
    }
}
 