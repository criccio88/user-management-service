package it.intesigroup.ums.dto;

import it.intesigroup.ums.domain.Role;
import it.intesigroup.ums.validation.CodiceFiscale;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public class UpdateUserRequest {
    @NotBlank
    @Size(max = 100)
    private String username;

    @NotBlank
    @CodiceFiscale
    private String codiceFiscale;

    @NotBlank
    @Size(max = 80)
    private String nome;

    @NotBlank
    @Size(max = 80)
    private String cognome;

    @NotEmpty
    private Set<Role> roles;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getCodiceFiscale() { return codiceFiscale; }
    public void setCodiceFiscale(String codiceFiscale) { this.codiceFiscale = codiceFiscale; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCognome() { return cognome; }
    public void setCognome(String cognome) { this.cognome = cognome; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
}
