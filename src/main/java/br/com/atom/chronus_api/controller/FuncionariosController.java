package br.com.atom.chronus_api.controller;

import br.com.atom.chronus_api.dtos.FuncionarioResponseDTO;
import br.com.atom.chronus_api.dtos.UpdateUserDTO;
import br.com.atom.chronus_api.dtos.UsersResponseDTO;
import br.com.atom.chronus_api.service.FuncionariosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/funcionarios")
@RequiredArgsConstructor
public class FuncionariosController {

    private final FuncionariosService funcionariosService;

    /**
     * GET /api/funcionarios          → lista todos via AFD
     * GET /api/funcionarios?pis=xxx  → filtra pelo PIS
     */
    @GetMapping
    public ResponseEntity<?> buscar(@RequestParam(required = false) String pis) {

        List<FuncionarioResponseDTO> lista = funcionariosService.listarFuncionarios();

        if (lista.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

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

        log.info("Listando todos: {} funcionário(s)", lista.size());
        return ResponseEntity.ok(lista);
    }

    /**
     * POST /api/funcionarios/importar
     * Importa empregados do relógio via /load_users.fcgi
     */
    @PostMapping("/importar")
    public ResponseEntity<UsersResponseDTO> importarEmpregados() {
        log.info("Iniciando importação de empregados");

        UsersResponseDTO resultado = funcionariosService.importarEmpregados();

        if (resultado == null) {
            return ResponseEntity.internalServerError().build();
        }

        log.info("Importação concluída: {} empregado(s)", resultado.getCount());
        return ResponseEntity.ok(resultado);
    }

    /**
     * PUT /api/funcionarios
     * Altera dados de um empregado no relógio via /update_users.fcgi
     */
    @PutMapping
    public ResponseEntity<String> atualizarEmpregado(@RequestBody UpdateUserDTO usuario) {
        log.info("Atualizando empregado PIS={}", usuario.getPis());

        String resultado = funcionariosService.atualizarEmpregado(usuario);

        if (resultado == null) {
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok(resultado);
    }

    /**
     * DELETE /api/funcionarios/{pis}
     * Remove empregado do relógio via /remove_users.fcgi
     */
    @DeleteMapping("/{pis}")
    public ResponseEntity<String> deletaFuncionario(@PathVariable long pis) {
        log.info("Deletando empregado PIS={}", pis);

        String resultado = funcionariosService.deletaFuncionario(pis);

        if (resultado == null) {
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok(resultado);
    }

}