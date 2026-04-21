package com.holidays.plan.model;


import jakarta.persistence.*;
import lombok.*;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "users")
@Data // Questo genera Getter, Setter, toString, Equals e HashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    private Role role; // ADMIN, MANAGER, USER

    private Double saldoFerie;

    // Ogni utente ha un solo manager
    @ManyToOne
    @JoinColumn(name = "manager_id")
    private User manager;

    // Un manager ha molti subordinati
    @OneToMany(mappedBy = "manager")
    @JsonIgnore // Evita loop infiniti quando trasformiamo in JSON
    private List<User> subordinates;
}