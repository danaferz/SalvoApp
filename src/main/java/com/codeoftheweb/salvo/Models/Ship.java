package com.codeoftheweb.salvo.Models;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
public class Ship {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    private long id;
    private String type;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "gamePlayer_id")
    private GamePlayer gamePlayer;

    @ElementCollection
    @Column(name = "shipLocation")
    Set<String> locations;

    public String getType() {
        return type;
    }

    public GamePlayer getGamePlayer() {
        return gamePlayer;
    }

    public Set<String> getLocations() {
        return locations;
    }

    public Ship() {
    }

    public Ship(GamePlayer gamePlayer, String type, Set<String> locations) {
        this.gamePlayer = gamePlayer;
        this.type = type;
        this.locations = locations;
    }

    public long getId() {
        return id;
    }

    public Map<String, Object> makeShipDTO() {
        Map<String, Object> shipDto = new LinkedHashMap<String, Object>();
        shipDto.put("type", this.getType());
        shipDto.put("locations", this.getLocations());
        return shipDto;
    }

}
