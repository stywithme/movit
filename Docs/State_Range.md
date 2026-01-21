

State



Name,is_required,color,Rate,is_rep_Counted
Perfect,True,Green,100%,True
Normal,False,Yellow,60%,True
Warning,False,Orange,20%,True
Error,False,Red,0%,False
Danger,False,Red,0%,False




        },
          "upRange": {
            "Perfect": { "min": 130, "max": 150 }, #required  - periorty 1 
            "normal":   { "min": 120, "max": 160 },  #متداخل مع Perfect    # Not Required
            "Pad": { "min": 110, "max": 160 }, #متداخل مع  - Perfect - Normal   # Not Required
            "Warning": { "min": 160, "max": 170 },  #لا يمكن ان يتداخل مع شئ   #Not Required
            "Danger": { "min": 170, "max": 180 }  #لا يمكن ان يتداخل مع شئ   #Not Required
          },
          "downRange": {
            "beginner": { "min": 30, "max": 80 },  #required  - periorty 1 
            "normal":   { "min": 0, "max": 30 }  #متداخل مع Perfect    # Not Required
          },